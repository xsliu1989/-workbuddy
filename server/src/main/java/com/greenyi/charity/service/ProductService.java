package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.entity.Product;
import com.greenyi.charity.mapper.CartMapper;
import com.greenyi.charity.mapper.OrderItemMapper;
import com.greenyi.charity.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品服务（公益商品 SALE / 免费申领品 CLAIM）
 */
@Service
public class ProductService {
    @Autowired private ProductMapper productMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private CartMapper cartMapper;

    public PageResult<Product> list(int current, int size, String kind) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        qw.eq(Product::getStatus, 1);
        if (StringUtils.hasText(kind)) qw.eq(Product::getKind, kind);
        qw.orderByDesc(Product::getCreatedAt);
        IPage<Product> page = productMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(page, p -> { fillCoverIfBlank(p); return p; });
    }

    public Product detail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null || (p.getStatus() != null && p.getStatus() == 0)) throw new BusinessException(404, "商品不存在或已下架");
        fillCoverIfBlank(p);
        return p;
    }

    /** 封面兜底：cover_url 为空时依次用 图片列表第一张、详情描述里的第一张图，避免列表/详情顶部空白 */
    private static final Pattern IMG_SRC = Pattern.compile("<img[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public void fillCoverIfBlank(Product p) {
        if (p == null) return;
        if (StringUtils.hasText(p.getCoverUrl())) return;
        String imgs = p.getImages();
        if (StringUtils.hasText(imgs)) {
            String first = imgs.split("[,，\\n]")[0].trim();
            if (!first.isEmpty()) { p.setCoverUrl(first); return; }
        }
        String desc = p.getDescription();
        if (StringUtils.hasText(desc)) {
            Matcher m = IMG_SRC.matcher(desc);
            if (m.find()) p.setCoverUrl(m.group(1).trim());
        }
    }

    // ---------- 后台管理 ----------
    public IPage<Product> adminList(int current, int size, String kind) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kind)) qw.eq(Product::getKind, kind);
        qw.orderByDesc(Product::getCreatedAt);
        return productMapper.selectPage(new Page<>(current, size), qw);
    }

    /** 后台单商品查询（含已下架，供编辑回填） */
    public Product adminDetail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "商品不存在");
        return p;
    }

    public Product save(Product p) {
        if (p.getName() == null || p.getName().trim().isEmpty()) throw new BusinessException(400, "商品名称不能为空");
        if (!"CLAIM".equals(p.getKind())) p.setKind("SALE");
        if (p.getPrice() == null) p.setPrice(java.math.BigDecimal.ZERO);
        if (p.getStock() == null) p.setStock(0);
        if (p.getWeightGram() == null) p.setWeightGram(0);
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getId() == null) productMapper.insert(p); else productMapper.updateById(p);
        return p;
    }

    public void toggleStatus(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "商品不存在");
        p.setStatus(p.getStatus() != null && p.getStatus() == 1 ? 0 : 1);
        productMapper.updateById(p);
    }

    /** 删除商品：已被订单引用的商品不允许删除（改为下架），避免历史订单商品丢失 */
    public void delete(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(404, "商品不存在");
        Long used = orderItemMapper.selectCount(new LambdaQueryWrapper<com.greenyi.charity.entity.OrderItem>()
                .eq(com.greenyi.charity.entity.OrderItem::getProductId, id));
        if (used != null && used > 0) {
            p.setStatus(0);
            productMapper.updateById(p);
            throw new BusinessException(400, "该商品已产生订单，不能删除，已为你下架");
        }
        cartMapper.delete(new LambdaQueryWrapper<com.greenyi.charity.entity.Cart>()
                .eq(com.greenyi.charity.entity.Cart::getProductId, id));
        productMapper.deleteById(id);
    }
}
