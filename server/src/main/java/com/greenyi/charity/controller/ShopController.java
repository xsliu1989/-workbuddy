package com.greenyi.charity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.common.Result;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 商品、购物车、订单 */
@RestController
@RequestMapping("/shop")
public class ShopController {
    @Autowired private ProductService productService;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;

    @GetMapping("/products")
    public Result<PageResult<Product>> products(@RequestParam(defaultValue = "1") int current,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String kind) {
        return Result.ok(productService.list(current, size, kind));
    }

    @GetMapping("/products/{id}")
    public Result<Product> productDetail(@PathVariable Long id) { return Result.ok(productService.detail(id)); }

    @GetMapping("/cart")
    public Result<List<Map<String, Object>>> cart() { return Result.ok(cartService.list()); }
    @PostMapping("/cart")
    public Result<?> addCart(@RequestBody Map<String, Object> body) { cartService.add(body); return Result.ok(); }
    @PutMapping("/cart")
    public Result<?> updateCart(@RequestBody Map<String, Object> body) { cartService.update(body); return Result.ok(); }
    @DeleteMapping("/cart/{id}")
    public Result<?> removeCart(@PathVariable Long id) { cartService.remove(id); return Result.ok(); }

    /** 结算试算 */
    @PostMapping("/checkout-preview")
    public Result<Map<String, Object>> preview(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = new java.util.HashMap<>();
        Object rp = body.get("requestPoints");
        Object aid = body.get("addressId");
        return Result.ok(orderService.checkoutPreview(rp == null ? null : Integer.parseInt(rp.toString()),
                aid == null ? null : Long.valueOf(aid.toString())));
    }

    @PostMapping("/order")
    public Result<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) { return Result.ok(orderService.createOrder(body)); }

    @PostMapping("/pay/confirm")
    public Result<Map<String, Object>> confirmPay(@RequestBody Map<String, Object> body) {
        return Result.ok(orderService.confirmPaid(String.valueOf(body.get("orderNo"))));
    }

    /** 支付回调（真实微信支付接入时指向此） */
    @PostMapping("/pay/notify/{orderNo}")
    public Result<Map<String, Object>> payNotify(@PathVariable String orderNo,
                                                 @RequestBody(required = false) String body,
                                                 @RequestHeader(value = "X-Sign", required = false) String sign) {
        return Result.ok(orderService.payNotify(orderNo, body, sign));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> myOrders() { return Result.ok(orderService.myOrders()); }
    /** 订单详情（含商品明细与物流状态，仅本人） */
    @GetMapping("/orders/{id}")
    public Result<Map<String, Object>> orderDetail(@PathVariable Long id) { return Result.ok(orderService.detail(id)); }
    @GetMapping("/orders/{id}/items")
    public Result<List<OrderItem>> orderItems(@PathVariable Long id) { return Result.ok(orderService.orderDetail(id)); }
    @PostMapping("/orders/{id}/refund")
    public Result<?> refund(@PathVariable Long id) { orderService.refund(id); return Result.ok(); }
    /** 买家申请退货退款，后台审核后退款 */
    @PostMapping("/orders/{id}/return-apply")
    public Result<?> applyReturn(@PathVariable Long id) { orderService.applyReturn(id); return Result.ok(); }
}
