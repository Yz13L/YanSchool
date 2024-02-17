package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author yzy
 * @since 2023-12-02
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    /**
     * 添加课表
     * @param userId
     * @param courseIds
     */

    void addUserLesson(Long userId, List<Long> courseIds);

    /**
     * 分页查询我的课表
     * @param pageQuery
     * @return
     */
    PageDTO<LearningLessonVO> queryMylessons(PageQuery pageQuery);

    /**
     * 查询正在学习的课程
     * @return
     */
    LearningLessonVO queryMyCurrentLesson();


    void deleteCourseFromLesson(Long userId, Long aLong);

    /**
     * 查询课程是否有效
     * @param courseId
     * @return
     */
    Long isLessonValid(Long courseId);

    /**
     * 查询用户课表中指定课程
     * @param courseId
     * @return
     */
    LearningLessonVO queryLessonByCourseId(Long courseId);

    /**
     * 创建学习计划
     * @param dto
     */
    void createLearningPlan(LearningPlanDTO dto);

    /**
     * 分页查询我的课程计划
     * @param query
     * @return
     */
    LearningPlanPageVO queryMyPlans(PageQuery query);
}
