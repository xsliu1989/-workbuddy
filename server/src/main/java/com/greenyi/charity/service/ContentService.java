package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.entity.Content;
import com.greenyi.charity.mapper.ContentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 内容宣传服务（图文/短视频/分享）
 */
@Service
public class ContentService {
    @Autowired private ContentMapper contentMapper;

    public PageResult<Content> list(int current, int size, String kind) {
        LambdaQueryWrapper<Content> qw = new LambdaQueryWrapper<Content>()
                .eq(Content::getStatus, "PUBLISHED");
        if (StringUtils.hasText(kind)) qw.eq(Content::getKind, kind);
        qw.orderByDesc(Content::getPublishedAt);
        IPage<Content> page = contentMapper.selectPage(new Page<>(current, size), qw);
        return PageResult.of(page, c -> c);
    }

    public Content detail(Long id) {
        Content c = contentMapper.selectById(id);
        if (c == null || !"PUBLISHED".equals(c.getStatus())) throw new BusinessException(404, "内容不存在");
        return c;
    }

    public void share(Long id) {
        Content c = contentMapper.selectById(id);
        if (c != null) { c.setShareCount((c.getShareCount() == null ? 0 : c.getShareCount()) + 1); contentMapper.updateById(c); }
    }

    // ---------- 后台 ----------
    public IPage<Content> adminList(int current, int size) {
        return contentMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Content>().orderByDesc(Content::getCreatedAt));
    }

    public Content save(Content c) {
        if (c.getId() == null) {
            c.setStatus("PUBLISHED");
            if (c.getPublishedAt() == null) c.setPublishedAt(new Date());
            contentMapper.insert(c);
        } else contentMapper.updateById(c);
        return c;
    }

    public void offline(Long id) {
        Content c = contentMapper.selectById(id);
        if (c != null) { c.setStatus("OFFLINE"); contentMapper.updateById(c); }
    }
}
