package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long id) {
        return followService.followOrNot(id);
    }

    @PutMapping("/{id}/{isFollow}")
    public Result isFollow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        return followService.isFollow(id, isFollow);
    }

    @GetMapping("/common/{id}")
    public Result common(@PathVariable("id") Long id) {
        return followService.common(id);
    }


}
