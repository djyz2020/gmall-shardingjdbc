package com.fwd.gmallshardingjdbc.impl;


import com.alibaba.druid.support.json.JSONUtils;
import com.fwd.gmallshardingjdbc.GetUserInfoService;
import com.fwd.gmallshardingjdbc.mybatis.mapper.OrderDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GetUserInfoServiceImpl implements GetUserInfoService{

    @Autowired
    private OrderDao orderDao;

    @Override
    public void getUserInfoById(String id, Model model) {
        List<Long> ids = new ArrayList<>();
        ids.add(778884965726158848L);
        ids.add(778884966867009537L);
        List<Map> maps = orderDao.selectOrderbyIds(ids);
        log.info(JSONUtils.toJSONString(maps));
    }
}
