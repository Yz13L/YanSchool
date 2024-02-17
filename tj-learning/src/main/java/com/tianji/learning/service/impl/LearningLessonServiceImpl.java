package com.tianji.learning.service.impl;

import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author yzy
 * @since 2023-12-02
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("ALL")
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    final CourseClient courseClient;
    final CatalogueClient catalogueClient;

    final LearningRecordMapper learningRecordMapper;

    @Override
    @Transactional
    public void addUserLesson(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量新增
        saveBatch(list);
    }


    /**
     * 分页查询我的课表
     * @param pageQuery
     * @return
     */

    @Override
    public PageDTO<LearningLessonVO> queryMylessons(PageQuery pageQuery) {
        //1.获取当前登陆人
        Long userId = UserContext.getUser();
        if(userId == null){
            throw  new BadRequestException("必须得登录");
        }

        //2.分页查询我的课表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        //3.远程调用课程服务，给vo中的课程名   封面  张结束赋值
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)){
            throw new BizIllegalException("课程不存在");
        }
        //将cinfos 课程集合  转换成 map结构<课程id  课程对象>
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //4.将po中的数据 封装到vo中
        List<LearningLessonVO> volist =new ArrayList<>();
        for (LearningLesson record : records) {
           /* LearningLessonVO vo = new LearningLessonVO();
            BeanUtils.copyProperties(record,vo);*/
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId()); //在map中获取对应的对象
            if(infoDTO != null){
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl(infoDTO.getCoverUrl());
                vo.setSections(infoDTO.getSectionNum());
            }
            volist.add(vo);

        }




        //5.返回
        return PageDTO.of(page,volist);
    }

    /**
     * 查询正在学习的课程
     * @return
     */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //1.获取当前用户id
        Long userId = UserContext.getUser();

        //2.查询当前用户最近学习课程    按latest_learn_time最近时间排序    正在学习中的
        //sql: select * from learning_lesson where user_id = xxx and status=1 order by lateest_learn_time desc limit 1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();

        //3.远程调用课程服务，给vo中的课程名   封面  张结束赋值
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null){
            throw new BizIllegalException("课程不存在");
        }
        //4.查询当前用户课表中   总的课程数
        //select count(*) from learning lesson where user_id = xxx
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();

        //5.通过feign远程调用课程服务     获取小节名称  和小节编号
        Long latestSectionId = lesson.getLatestSectionId(); //最近学习的小节id
        //使用单个集合的方法去进行。比较节省空间，默认创建集合的大小是十个
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));

        //6.封装到vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(count);   //当前用户能学习课程总数
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());  //最近学习的小节
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex()); //最近学习的小节序号

        return vo;
    }

    /**
     * 根据用户id和课程id进行删除
     * @param userId
     * @param courseId
     */
    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        // 1.获取当前登录用户
        if (userId == null) {
            userId = UserContext.getUser();
        }
        // 2.删除课程
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    /**
     * 查询课程是否有效
     * @param courseId
     * @return
     */
    @Override
    public Long isLessonValid(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.查询课表learning_lesson     条件 user_id 和 course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            return null;
        }
        //3.判断课程状态是否有效
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(expireTime)){
            return null;
        }

        //4.返回
        return lesson.getId();
    }

    /**
     * 查询用户课表中指定课程
     * @param courseId
     * @return
     */
    @Override

    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.获取课程learning_lesson 条件 user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            return null;
        }
        //3.将po转换为vo
        LearningLessonVO lessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        //4.返回vo
        return lessonVO;
    }


    /**
     * 创建学习计划
     * @param dto
     */
    @Override
    public void createLearningPlan(LearningPlanDTO dto) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.获取课程learning_lesson 条件 user_id course_id
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if(lesson == null){
            throw new BizIllegalException("该课程没有加入课表");
        }
        //3.修改课表
        /*lesson.setWeekFreq(dto.getFreq());
        lesson.setPlanStatus(dto.getPlanStatus());
        this.updateById(lesson);*/
        //链式编程
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq,dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId,lesson.getId())
                .update();
    }

    /**
     * 分页查询我的课程计划
     * @param query
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //todo 2.查询几份
        //3.查询本周学习计划总数据 learning_lesson 条件userId    status  in(0,1) plan_status=1  ， 查询sum(week_freq)
        //sql   select sum(week_freq) from learning_lesson where user_id = 2 and plan_status = 1
        //      and status in(0,1)
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id",userId);
        wrapper.in("status",LessonStatus.NOT_BEGIN,LessonStatus.LEARNING);
        wrapper.eq("plan_status",PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);//map有可能为null
        //{plansTotal:7}
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null){
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
        //4.查询本周  实际  已学习计划总数据  learning_record 条件userId finish_time仔本周区间   finished为true   count(*)
        //* SELECT COUNT(*) FROM learning_record
        //where user_id = 2
        //AND finished = 1    AND finish_time BETWEEN '周一 00:00:01' AND '周日23:59:59'
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);//本周开始时间
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);//本周结束时间
        Integer weekFinishedPlanNum = learningRecordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
        //5.查询课表数据  learning_lessons    条件userId    status  in(0,1) plan_status=1   分页
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            LearningPlanPageVO voNull = new LearningPlanPageVO();
            voNull.setTotal(0L);
            voNull.setPages(0L);
            voNull.setList(CollUtils.emptyList());
            return voNull;
        }
        //6.远程调用课程服务    获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)){
            throw new BizIllegalException("课程不存在");
        }
        //将cinfo list结构转map <课程id, CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> cinfoMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //7.查询学习记录表 lreaning_record 本周 当前用户下   每一门课下   已学习的小节数量
        //* SELECT lesson_id,COUNT(*) FROM learning_record
        //where user_id = 2
        //AND finished = 1    AND finish_time BETWEEN '周一 00:00:01' AND '周日23:59:59'
        //GROUP BY lesson_id
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id as lessonId","count(*) as userId");  //由于没有对应属性 临时用userId来存储count(*)
        rWrapper.eq("user_id",userId);
        rWrapper.eq("finished",true);
        rWrapper.between("finish_time",weekBeginTime,weekEndTime);
        rWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = learningRecordMapper.selectList(rWrapper);
        //将集合转换成map     map中的key是 lessonId value是 当前用户对该课程下已学习的小节数量
        Map<Long, Long> courseweekFinishNumMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));
        //8.封装vo返回
        LearningPlanPageVO vo =new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            CourseSimpleInfoDTO infoDto = cinfoMap.get(record.getCourseId());
            if (infoDto != null){
                planVO.setCourseName(infoDto.getName());
                planVO.setSections(infoDto.getSectionNum());
            }
            Long aLong = courseweekFinishNumMap.get(record.getId());
           /* if (aLong !=null){
                planVO.setWeekLearnedSections();    //本周已学习章节数
            }else {
                planVO.setWeekLearnedSections(0);
            }*/
            planVO.setWeekLearnedSections(courseweekFinishNumMap.getOrDefault(record.getId(),0L).intValue());
            voList.add(planVO);
        }
        vo.setList(voList);
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());
        return vo;
    }

    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        return queryWrapper;
    }

}
