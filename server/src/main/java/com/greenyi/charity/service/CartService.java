package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.entity.Cart;
import com.greenyi.charity.entity.Product;
import com.greenyi.charity.mapper.CartMapper;
import com.greenyi.charity.mapper.ProductMapper;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务（FA-011 加购校验库存）
 */
@Service
public class CartService {
    @Autowired private CartMapper cartMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private ProductService productService;

    /** 购物车视图：联商品信息 */
    public List<Map<String, Object>> list() {
        Long userId = UserContext.requireUserId();
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId).orderByDesc(Cart::getCreatedAt));
        return carts.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            Product p = productMapper.selectById(c.getProductId());
            m.put("id", c.getId());
            m.put("productId", c.getProductId());
            m.put("name", p == null ? "已下架" : p.getName());
            m.put("price", p == null ? null : p.getPrice());
            if (p != null) productService.fillCoverIfBlank(p);
            m.put("coverUrl", p == null ? null : p.getCoverUrl());
            m.put("quantity", c.getQuantity());
            m.put("selected", c.getSelected());
            m.put("stock", p == null ? 0 : p.getStock());
            return m;
        }).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void add(Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Long productId = Long.valueOf(body.get("productId").toString());
        int quantity = body.get("quantity") == null ? 1 : Integer.parseInt(body.get("quantity").toString());
        Product p = productMapper.selectById(productId);
        if (p == null || (p.getStatus() != null && p.getStatus() == 0)) throw new BusinessException(404, "商品不存在或已下架");
        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId).eq(Cart::getProductId, productId));
        int target = (exist == null ? 0 : exist.getQuantity()) + quantity;
        if (target > p.getStock()) throw new BusinessException(400, "库存不足");
        if (exist == null) {
            Cart c = new Cart();
            c.setUserId(userId); c.setProductId(productId); c.setQuantity(quantity); c.setSelected(1);
            cartMapper.insert(c);
        } else {
            exist.setQuantity(target);
            cartMapper.updateById(exist);
        }
    }

    public void update(Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Cart c = cartMapper.selectById(Long.valueOf(body.get("id").toString()));
        if (c == null || !c.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        if (body.get("quantity") != null) {
            int q = Integer.parseInt(body.get("quantity").toString());
            if (q < 1) throw new BusinessException(400, "数量至少为1");
            Product p = productMapper.selectById(c.getProductId());
            if (p != null && q > p.getStock()) throw new BusinessException(400, "库存不足");
            c.setQuantity(q);
        }
        if (body.get("selected") != null) c.setSelected(Integer.parseInt(body.get("selected").toString()));
        cartMapper.updateById(c);
    }

    public void remove(Long cartId) {
        Long userId = UserContext.requireUserId();
        Cart c = cartMapper.selectById(cartId);
        if (c == null || !c.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");
        cartMapper.deleteById(cartId);
    }
}
