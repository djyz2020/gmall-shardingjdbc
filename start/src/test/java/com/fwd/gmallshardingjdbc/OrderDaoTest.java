package com.fwd.gmallshardingjdbc;

import com.fwd.gmallshardingjdbc.mybatis.mapper.OrderDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GmallShardingjdbcApplication.class})
public class OrderDaoTest {

    @Autowired
    private OrderDao orderDao;

    @Test
    public void insertOrderTest() {
        for (int i = 0; i < 200; i++) {
            orderDao.insertOrder(i + 1, 1L, "WAIT_PAY");
        }
    }

    @Test
    public void selectOrderbyIdsTest() {
        List<Long> ids = new ArrayList<>();
        ids.add(779848503147888640L);
        List<Map> maps = orderDao.selectOrderbyIds(ids);
        maps = orderDao.selectOrderbyIds(ids);
        maps = orderDao.selectOrderbyIds(ids);
        System.out.println(maps);
    }

}

