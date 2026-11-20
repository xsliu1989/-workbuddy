package com.greenyi.charity.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.integration.PaymentGateway;
import com.greenyi.charity.mapper.*;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 订单服务：结算试算（积分抵扣）-> 创建订单 -> 微信支付 -> 回调幂等 -> 发货 -> 退款回退积分
 * 运费不可用积分抵扣（FR-010/AC-06）
 */
@Service
public class OrderService {
    @Autowired private CartMapper cartMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private OrdersMapper orderMapper;
    @Autowired private OrderItemMapper itemMapper;
    @Autowired private PointService pointService;
    @Autowired private PaymentGateway paymentGateway;
    @Autowired private UserAddressMapper addressMapper;

    /** 结算试算：货款（选中商品）+ 积分抵扣 + 运费 */
    public Map<String, Object> checkoutPreview(Integer requestPoints, Long addressId) {
        Long userId = UserContext.requireUserId();
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId).eq(Cart::getSelected, 1));
        if (carts.isEmpty()) throw new BusinessException(400, "请先选择商品");

        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (Cart c : carts) {
            Product p = productMapper.selectById(c.getProductId());
            if (p == null || (p.getStatus() != null && p.getStatus() == 0)) throw new BusinessException(400, "购物车含已下架商品");
            if (c.getQuantity() > p.getStock()) throw new BusinessException(400, "「" + p.getName() + "」库存不足");
            goodsAmount = goodsAmount.add(p.getPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
        }
        // 运费：公益商品订单暂按固定模板（运费计算主要用于免费申领），此处为 0 或可配置
        BigDecimal freight = BigDecimal.ZERO;
        if (addressId != null) {
            // 预留：可按地址计算运费，商品订单默认包邮
        }

        Map<String, Object> discount = pointService.calcDiscount(userId, goodsAmount, requestPoints);
        BigDecimal discountAmount = (BigDecimal) discount.get("discountAmount");
        BigDecimal payAmount = goodsAmount.subtract(discountAmount).add(freight);

        Map<String, Object> res = new HashMap<>();
        res.put("items", carts);
        res.put("goodsAmount", goodsAmount);
        res.put("freightAmount", freight);
        res.put("discount", discount);
        res.put("discountAmount", discountAmount);
        res.put("payAmount", payAmount);
        return res;
    }

    /** 创建订单并拉起支付 */
    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Integer requestPoints = body.get("requestPoints") == null ? null : Integer.parseInt(body.get("requestPoints").toString());
        Long addressId = body.get("addressId") == null ? null : Long.valueOf(body.get("addressId").toString());

        Map<String, Object> preview = checkoutPreview(requestPoints, addressId);
        @SuppressWarnings("unchecked") Map<String, Object> discount = (Map<String, Object>) preview.get("discount");
        int applyPoints = (int) discount.get("applyPoints");
        BigDecimal goodsAmount = (BigDecimal) preview.get("goodsAmount");
        BigDecimal discountAmount = (BigDecimal) preview.get("discountAmount");
        BigDecimal freight = (BigDecimal) preview.get("freightAmount");
        BigDecimal payAmount = (BigDecimal) preview.get("payAmount");

        List<Cart> carts = (List<Cart>) preview.get("items");

        // 创建订单
        Orders order = new Orders();
        order.setOrderNo("SO" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        order.setUserId(userId);
        order.setAddressId(addressId);
        order.setGoodsAmount(goodsAmount);
        order.setFreightAmount(freight);
        order.setPointsUsed(applyPoints);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(payAmount);
        order.setPayStatus("UNPAID");
        order.setStatus("PENDING_PAY");
        orderMapper.insert(order);

        // 订单明细 + 扣库存
        for (Cart c : carts) {
            Product p = productMapper.selectById(c.getProductId());
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId()); oi.setProductId(p.getId()); oi.setName(p.getName());
            oi.setPrice(p.getPrice()); oi.setQuantity(c.getQuantity());
            itemMapper.insert(oi);
            p.setStock(p.getStock() - c.getQuantity());
            productMapper.updateById(p);
        }

        // 拉起支付（mock）
        AppUser user = userMapper.selectById(userId);
        int amountCent = payAmount.multiply(BigDecimal.valueOf(100)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        PaymentGateway.PayParams payParams = paymentGateway.createPayment(
                order.getOrderNo(), "绿益公益商品订单", amountCent,
                user == null ? "" : user.getOpenid());

        Map<String, Object> res = new HashMap<>();
        res.put("orderId", order.getId());
        res.put("orderNo", order.getOrderNo());
        res.put("payAmount", payAmount);
        res.put("applyPoints", applyPoints);
        res.put("payParams", payParams);
        return res;
    }

    /** 支付回调（模拟/真实）：幂等处理，校验通过才置为已支付并核销积分 */
    @Transactional
    public Map<String, Object> payNotify(String orderNo, String body, String signature) {
        String txnId;
        try {
            txnId = paymentGateway.verifyNotify(body == null ? "{\"transaction_id\":\"mock\"}" : body, signature);
        } catch (Exception e) {
            throw new BusinessException(400, "支付回调校验失败");
        }
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (order == null) throw new BusinessException(404, "订单不存在");
        if ("PAID".equals(order.getPayStatus())) {
            Map<String, Object> res = new HashMap<>();
            res.put("duplicate", true); res.put("orderNo", orderNo);
            return res; // 幂等：重复回调直接返回
        }
        order.setPayStatus("PAID");
        order.setStatus("PAID");
        order.setTransactionId(txnId);
        order.setPaidAt(new Date());
        orderMapper.updateById(order);

        if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
            pointService.consume(order.getUserId(), order.getPointsUsed(), order.getOrderNo());
        }
        // 清除已购商品
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, order.getUserId()).eq(Cart::getSelected, 1));

        Map<String, Object> res = new HashMap<>();
        res.put("duplicate", false); res.put("orderNo", orderNo); res.put("status", "PAID");
        return res;
    }

    /** 主动确认支付（小程序 mock 支付完成后调用，等价于回调） */
    public Map<String, Object> confirmPaid(String orderNo) {
        return payNotify(orderNo, "{\"transaction_id\":\"mock_" + orderNo + "\"}", "mock");
    }

    /** 订单视图：订单字段 + 商品明细 + 商品摘要，便于小程序与后台展示已售商品 */
    private Map<String, Object> orderView(Orders o, boolean withBuyer) {
        Map<String, Object> m = BeanUtil.beanToMap(o);
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, o.getId()).orderByAsc(OrderItem::getId));
        m.put("items", items);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            OrderItem it = items.get(i);
            if (i > 0) sb.append("、");
            sb.append(it.getName()).append(" ×").append(it.getQuantity());
        }
        m.put("itemSummary", sb.toString());
        if (o.getAddressId() != null) {
            UserAddress a = addressMapper.selectById(o.getAddressId());
            if (a != null) {
                Map<String, Object> addr = new HashMap<>();
                addr.put("receiver", a.getReceiver());
                addr.put("phone", a.getPhone());
                addr.put("province", a.getProvince());
                addr.put("city", a.getCity());
                addr.put("district", a.getDistrict());
                addr.put("detail", a.getDetail());
                addr.put("fullAddress", (a.getProvince() == null ? "" : a.getProvince())
                        + (a.getCity() == null ? "" : a.getCity())
                        + (a.getDistrict() == null ? "" : a.getDistrict())
                        + (a.getDetail() == null ? "" : a.getDetail()));
                m.put("address", addr);
            }
        }
        if (withBuyer && o.getUserId() != null) {
            AppUser u = userMapper.selectById(o.getUserId());
            m.put("userName", u == null ? String.valueOf(o.getUserId())
                    : (u.getNickname() == null ? String.valueOf(o.getUserId()) : u.getNickname()));
        }
        return m;
    }

    /** 我的订单（含商品明细） */
    public List<Map<String, Object>> myOrders() {
        Long userId = UserContext.requireUserId();
        List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId).orderByDesc(Orders::getCreatedAt));
        List<Map<String, Object>> res = new ArrayList<>();
        for (Orders o : orders) res.add(orderView(o, false));
        return res;
    }

    /** 订单详情（仅本人） */
    public Map<String, Object> detail(Long id) {
        Long userId = UserContext.requireUserId();
        Orders o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException(404, "订单不存在");
        if (!o.getUserId().equals(userId)) throw new BusinessException(403, "无权查看");
        return orderView(o, false);
    }

    /** 订单商品明细（兼容原 /shop/orders/{id}/items 接口） */
    public List<OrderItem> orderDetail(Long orderId) {
        Long userId = UserContext.requireUserId();
        Orders o = orderMapper.selectById(orderId);
        if (o == null) throw new BusinessException(404, "订单不存在");
        if (!o.getUserId().equals(userId)) throw new BusinessException(403, "无权查看");
        return itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId).orderByAsc(OrderItem::getId));
    }

    /** 管理员订单列表：可按状态筛选，含购买人与已售商品明细 */
    public List<Map<String, Object>> adminOrders(String status) {
        LambdaQueryWrapper<Orders> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) q.eq(Orders::getStatus, status);
        q.orderByDesc(Orders::getCreatedAt);
        List<Orders> orders = orderMapper.selectList(q);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Orders o : orders) res.add(orderView(o, true));
        return res;
    }

    /** 管理员单订单详情：含收货地址与商品明细 */
    public Map<String, Object> adminOrderDetail(Long id) {
        Orders o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException(404, "订单不存在");
        return orderView(o, true);
    }

    /** 买家申请退货退款：订单进入退货审核，等待后台处理 */
    @Transactional
    public void applyReturn(Long orderId) {
        Long userId = UserContext.requireUserId();
        Orders o = orderMapper.selectById(orderId);
        if (o == null) throw new BusinessException(404, "订单不存在");
        if (!o.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        if (!"PAID".equals(o.getPayStatus())) throw new BusinessException(400, "订单未支付，无需退货");
        if ("RETURN_APPLYING".equals(o.getStatus()) || "REFUNDED".equals(o.getStatus())) {
            throw new BusinessException(400, "该订单已在售后处理中");
        }
        o.setStatus("RETURN_APPLYING");
        orderMapper.updateById(o);
    }

    /** 后台同意退货：走统一退款逻辑（回退库存与积分） */
    @Transactional
    public void approveReturn(Long orderId) { refund(orderId); }

    /** 后台拒绝退货：恢复为已发货，买家可继续收货 */
    public void rejectReturn(Long orderId) {
        Orders o = orderMapper.selectById(orderId);
        if (o == null) throw new BusinessException(404, "订单不存在");
        if (!"RETURN_APPLYING".equals(o.getStatus())) throw new BusinessException(400, "订单不在退货审核中");
        o.setStatus("SHIPPED");
        orderMapper.updateById(o);
    }

    /** 退款：回退库存、积分 */
    @Transactional
    public void refund(Long orderId) {
        Orders o = orderMapper.selectById(orderId);
        if (o == null || !"PAID".equals(o.getPayStatus())) throw new BusinessException(400, "订单不可退款");
        o.setPayStatus("REFUNDED"); o.setStatus("REFUNDED");
        orderMapper.updateById(o);
        List<OrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem oi : items) {
            Product p = productMapper.selectById(oi.getProductId());
            if (p != null) { p.setStock(p.getStock() + oi.getQuantity()); productMapper.updateById(p); }
        }
        if (o.getPointsUsed() != null && o.getPointsUsed() > 0) {
            pointService.refund(o.getUserId(), o.getPointsUsed(), o.getOrderNo());
        }
    }

    /** 后台发货：记录物流单号并进入已发货状态 */
    public void ship(Long orderId, String logisticsNo) {
        Orders o = orderMapper.selectById(orderId);
        if (o == null) throw new BusinessException(404, "订单不存在");
        o.setStatus("SHIPPED");
        o.setLogisticsNo(logisticsNo == null ? "" : logisticsNo.trim());
        orderMapper.updateById(o);
    }

    /** 后台标记已完成：物流跟踪闭环 */
    public void complete(Long orderId) {
        Orders o = orderMapper.selectById(orderId);
        if (o == null) throw new BusinessException(404, "订单不存在");
        o.setStatus("COMPLETED");
        orderMapper.updateById(o);
    }

    // 避免字段注入循环：懒引入 AppUserMapper
    private AppUserMapper userMapper;
    @Autowired
    public void setUserMapper(AppUserMapper userMapper) { this.userMapper = userMapper; }
}
