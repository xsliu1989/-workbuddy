package com.greenyi.charity.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果与对象转换
 */
@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(page.getRecords().stream().map(converter).collect(Collectors.toList()));
        r.setTotal(page.getTotal());
        r.setCurrent(page.getCurrent());
        r.setSize(page.getSize());
        return r;
    }
}
