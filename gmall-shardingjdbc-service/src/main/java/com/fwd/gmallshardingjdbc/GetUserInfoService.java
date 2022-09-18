package com.fwd.gmallshardingjdbc;

import org.springframework.ui.Model;

public interface GetUserInfoService {

    void getUserInfoById(String id, Model model);

}
