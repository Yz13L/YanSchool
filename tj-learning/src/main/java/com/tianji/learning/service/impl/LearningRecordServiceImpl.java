package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author yzy
 * @since 2023-12-06
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService iLearningLessonService;

    private final CourseClient courseClient;

    /**
     * 根据id查询指定课程的学习记录
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取当前用户登录id
        Long userId = UserContext.getUser();
        //2.根据用户id和课程id查询课表信息   条件user_id 和courseID
        LearningLesson lesson = iLearningLessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            throw new BizIllegalException("该课程未加入课表");
        }
        //3.查询学习记录  条件lesson_id 和user_id
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .eq(LearningRecord::getUserId, userId)
                .list();

        //4.封装结果返回
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> recordDTOS = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        dto.setRecords(recordDTOS);
        return dto;
    }

    /**
     * 提交学习记录
     * @param dto
     */
    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.处理学习记录 判断为视频还是考试
        boolean isFinished =false;//代表本小节是否第一次已学完
        if(dto.getSectionType().equals(SectionType.VIDEO)){
            //2.1提交视频播放记录   使用封装方法  返回本小节是否已学完的状态
            isFinished = handleVideoRecord(userId,dto);
        }else {
            //2.2提交考试记录     使用封装方法    返回本小节是否已学完的状态
            isFinished = handleExamRecord(userId,dto);
        }
        if(!isFinished){
            // 没有新学完的小节，无需更新课表中的学习进度
            return;
        }
        //3.处理课表数据
        handleLessonDate(dto,isFinished);
    }


    //处理课表相关数据
    private void handleLessonDate(LearningRecordFormDTO dto, boolean isFinished) {
        //1.查询课表 learning_lesson 条件lesson_id主键
        LearningLesson lesson = iLearningLessonService.getById(dto.getLessonId());
        if (lesson == null){
            throw new BizIllegalException("课表不存在");
        }
        //2.判断是否为第一次学完  isFinished是不是true
        boolean allFninished = false;   //所有小节是否已学完
        if(isFinished){
            //3.远程调用课程服务    得到课程信息  小节总数
            CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cinfo==null){
                throw new BizIllegalException("课程不存在");
            }
            Integer sectionNum = cinfo.getSectionNum(); //该课程下小节总数
            //4.如果isFinished为true 本小节是第一次学完     判断该用户对该课程是否完成学完
            Integer learnedSections = lesson.getLearnedSections();  //当前用户对该课程已学的课程数
            allFninished = learnedSections + 1 >= sectionNum;   //所有小节是否学完
        }
        //5.更新课表数据
        iLearningLessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN,LearningLesson::getStatus,LessonStatus.LEARNING)
                .set(allFninished,LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId,dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime,dto.getCommitTime())
                .setSql(isFinished,"learned_sections = learned_sections + 1")       //用sql防止线程并发过高导致都为1
                .eq(LearningLesson::getId,lesson.getId())
                .update();
    }

    //处理考试记录
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        //1.将dto转换po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);   //提交考试记录 代表本小节已学完
        record.setFinishTime(dto.getCommitTime());  //完成时间为dto提交时间
        //2.保存学习记录learning_record
        boolean result = this.save(record);
        if (!result){
            throw new DbException("新增考试记录失败");
        }
        return true;
    }

    //处理视频播放记录
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        //1.查询旧的学习记录 learning_record    条件userId lessonId section_id
        LearningRecord learningRecord = this.lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getLessonId, dto.getLessonId())
                .eq(LearningRecord::getSectionId, dto.getSectionId())
                .one();
        //2.判断是否存在
        if (learningRecord == null){
            //3 如果不存在则新增学习记录
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            boolean result = this.save(record);
            if (!result){
                throw new DbException("新增考试记录失败");
            }
            return false;
        }
        //4.如果存在更新学习记录  learning_record 更新什么字段 moment
        //判断本小节是否是第一次学完     isFinshed为true代表第一次学完
        boolean isFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        // update learning_record set moment = xxx , finished = true, finish_time == xxxx where id = xxx
        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFinished, LearningRecord::getFinished, true)
                .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if(!result){
            throw new DbException("更新视频学习记录失败");
        }
        return isFinished;
    }


}
