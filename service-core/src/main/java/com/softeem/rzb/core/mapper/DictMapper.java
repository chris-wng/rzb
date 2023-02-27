package com.softeem.rzb.core.mapper;

import com.softeem.rzb.core.pojo.dto.ExcelDictDTO;
import com.softeem.rzb.core.pojo.entity.Dict;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 * 数据字典 Mapper 接口
 * </p>
 *
 * @author chris
 * @since 2023-02-08
 */
public interface DictMapper extends BaseMapper<Dict> {

    void insertBatch(List<ExcelDictDTO> list);
}
