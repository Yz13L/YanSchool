package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.file.LinkOption;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author yzy
 * @since 2023-12-02
 */
@Api(tags = "我的课程相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
@Slf4j
public class LearningLessonController {


    final ILearningLessonService iLearningLessonService;

    @ApiOperation("分页查询我的课表")
    @GetMapping("page")
    public PageDTO<LearningLessonVO> queryMylessons(PageQuery pageQuery){
        log.info("分页查询我的课表");
        return iLearningLessonService.queryMylessons(pageQuery);

    }


    @ApiOperation("查询正在学习的课程")
    @GetMapping("now")
    public LearningLessonVO queryMyCurrentLesson(){
        return iLearningLessonService.queryMyCurrentLesson();
    }

    /**
     * 检查课程是否有效
     * @param courseId
     * @return
     */
    @ApiOperation("检查课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        log.info("检查课程是否有效");
        return iLearningLessonService.isLessonValid(courseId);
    }

    /**
     * 查询用户课表中指定课程
     * @param courseId
     * @return
     */
    @GetMapping("/{courseId}")
    @ApiOperation("查询用户课表中指定课程状态")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId){
        return iLearningLessonService.queryLessonByCourseId(courseId);
    }

    /**
     * 创建学习计划
     */
    @PostMapping("plans")
    @ApiOperation("创建学习计划")
    public void createLearningPlan(@RequestBody @Validated LearningPlanDTO dto){
        iLearningLessonService.createLearningPlan(dto);
    }

    /**
     * 分页查询我的课程计划
     * @param query
     * @return
     */
    @ApiOperation("分页查询我的课程计划")
    @GetMapping("plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return iLearningLessonService.queryMyPlans(query);
    }


}
