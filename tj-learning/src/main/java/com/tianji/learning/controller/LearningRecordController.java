package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author yzy
 * @since 2023-12-06
 */
@RestController
@Api(tags = "学习记录相关接口")
@RequiredArgsConstructor
@RequestMapping("/learning-record")
public class LearningRecordController {

    private final ILearningRecordService iLearningRecordService;


    /**
     * 查询当前用户指定课程的学习进度
     * @param courseId 课程id
     * @return 课表信息、学习记录及进度信息
     */
    @ApiOperation("查询当前用户指定课程的学习进度")
    @GetMapping("/course/{courseId}")
    LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId){
        return iLearningRecordService.queryLearningRecordByCourse(courseId);

    }

    /**
     * 提交学习记录
     * @param dto
     */
    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO dto){
        iLearningRecordService.addLearningRecord(dto);

    }

}
