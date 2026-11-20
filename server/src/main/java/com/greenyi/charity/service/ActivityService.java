package com.greenyi.charity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenyi.charity.common.BusinessException;
import com.greenyi.charity.common.PageResult;
import com.greenyi.charity.entity.*;
import com.greenyi.charity.mapper.*;
import com.greenyi.charity.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 活动与志愿者服务（FA-050~054）
 * 发布 -> 报名（先到先得无审核、满员即止）-> 签到记时长 -> 活动结束发官方证书 -> 时长并入荣誉档案
 */
@Service
public class ActivityService {
    private static final Pattern IMG_SRC = Pattern.compile("<img[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Autowired private ActivityMapper activityMapper;
    @Autowired private VolunteerSignupMapper signupMapper;
    @Autowired private CertificateService certificateService;
    @Autowired private HonorService honorService;
    @Autowired private AppUserMapper userMapper;
    @Autowired private AiAuditLogMapper aiLogMapper;
    @Autowired private com.greenyi.charity.integration.AiService aiService;

    public PageResult<Activity> list(int current, int size) {
        IPage<Activity> page = activityMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Activity>().in(Activity::getStatus, Arrays.asList("PUBLISHED", "ONGOING", "FINISHED"))
                        .orderByDesc(Activity::getCreatedAt));
        return PageResult.of(page, a -> { fillCoverIfBlank(a); return a; });
    }

    public Activity detail(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(404, "活动不存在");
        fillCoverIfBlank(a);
        return a;
    }

    /** 未上传封面时，用详情里的第一张图作为活动背景图 */
    private void fillCoverIfBlank(Activity a) {
        if (a == null || a.getCoverUrl() != null && !a.getCoverUrl().trim().isEmpty()) return;
        if (a.getDescription() == null || a.getDescription().trim().isEmpty()) return;
        Matcher m = IMG_SRC.matcher(a.getDescription());
        if (m.find()) a.setCoverUrl(m.group(1));
    }

    /** 志愿者报名：先到先得，满员即止，无审核。支持填写报名人姓名/手机号，可替他人报名 */
    @Transactional
    public VolunteerSignup signup(Long activityId, Map<String, Object> body) {
        Long userId = UserContext.requireUserId();
        Activity a = activityMapper.selectById(activityId);
        if (a == null || "CANCELLED".equals(a.getStatus())) throw new BusinessException(400, "活动不可报名");
        if (a.getSignedCount() != null && a.getQuota() != null && a.getSignedCount() >= a.getQuota()) {
            throw new BusinessException(400, "报名名额已满");
        }
        body = body == null ? new HashMap<>() : body;
        String name = str(body.get("name"));
        String phone = str(body.get("phone"));
        AppUser u = userMapper.selectById(userId);

        // 手机号必填且格式校验（11 位中国大陆手机号）
        if (phone == null || !phone.matches("1\\d{10}")) throw new BusinessException(400, "请填写正确的 11 位手机号");
        // 姓名必填，缺省用登录用户昵称兜底
        if (name == null || name.trim().isEmpty()) name = u == null ? null : u.getNickname();
        if (name == null || name.trim().isEmpty()) throw new BusinessException(400, "请填写报名人姓名");
        name = name.trim();

        // 同一活动同一手机号不可重复报名（支持同一账号替不同人报名）
        VolunteerSignup exist = signupMapper.selectOne(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(VolunteerSignup::getActivityId, activityId).eq(VolunteerSignup::getPhone, phone));
        if (exist != null) throw new BusinessException(400, "该手机号已报名该活动");

        VolunteerSignup s = new VolunteerSignup();
        s.setActivityId(activityId); s.setUserId(userId);
        s.setSignupName(name); s.setPhone(phone);
        s.setGuardianAuth(u == null ? 0 : (u.getGuardianAuth() == null ? 0 : u.getGuardianAuth()));
        s.setStatus("SIGNED_UP");
        signupMapper.insert(s);
        a.setSignedCount((a.getSignedCount() == null ? 0 : a.getSignedCount()) + 1);
        activityMapper.updateById(a);
        return s;
    }

    /** 我的报名 */
    public List<Map<String, Object>> mySignups() {
        Long userId = UserContext.requireUserId();
        List<VolunteerSignup> list = signupMapper.selectList(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(VolunteerSignup::getUserId, userId).orderByDesc(VolunteerSignup::getCreatedAt));
        List<Map<String, Object>> res = new ArrayList<>();
        for (VolunteerSignup s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId()); m.put("status", s.getStatus());
            m.put("signupName", s.getSignupName()); m.put("phone", s.getPhone());
            m.put("hoursMinutes", s.getHoursMinutes()); m.put("certId", s.getCertId());
            Activity a = activityMapper.selectById(s.getActivityId());
            m.put("activityTitle", a == null ? "已删除活动" : a.getTitle());
            m.put("activityId", s.getActivityId());
            res.add(m);
        }
        return res;
    }

    /** 后台签到：记录时长 */
    public void checkin(Long signupId, Integer minutes) {
        VolunteerSignup s = signupMapper.selectById(signupId);
        if (s == null) throw new BusinessException(404, "报名记录不存在");
        s.setStatus("CHECKED_IN"); s.setCheckinAt(new Date());
        s.setHoursMinutes(minutes == null ? 0 : minutes);
        signupMapper.updateById(s);
    }

    /** 活动结束：为所有签到的志愿者签发官方证书 + 时长并入荣誉档案 */
    @Transactional
    public Map<String, Object> finish(Long activityId) {
        Activity a = activityMapper.selectById(activityId);
        if (a == null) throw new BusinessException(404, "活动不存在");
        a.setStatus("FINISHED");
        activityMapper.updateById(a);

        List<VolunteerSignup> signups = signupMapper.selectList(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(VolunteerSignup::getActivityId, activityId).eq(VolunteerSignup::getStatus, "CHECKED_IN"));
        int issued = 0;
        for (VolunteerSignup s : signups) {
            if (s.getCertId() != null) continue; // 幂等
            AppUser u = userMapper.selectById(s.getUserId());
            // 证书持有人优先用报名人姓名（支持替他人报名）
            String holder = (s.getSignupName() != null && !s.getSignupName().trim().isEmpty())
                    ? s.getSignupName() : (u == null ? null : u.getNickname());
            Certificate cert = certificateService.issueVolunteerCert(s.getUserId(), activityId, holder, a.getTitle());
            s.setCertId(cert.getId());
            signupMapper.updateById(s);
            if (s.getHoursMinutes() != null && s.getHoursMinutes() > 0) {
                honorService.addVolunteerHours(s.getUserId(), s.getHoursMinutes());
            }
            issued++;
        }
        Map<String, Object> res = new HashMap<>();
        res.put("activityId", activityId); res.put("certIssued", issued);
        return res;
    }

    /** 后台报名名单：报名人姓名/手机号 + 报名账号昵称 + 活动名称 */
    public List<Map<String, Object>> adminSignups(Long activityId) {
        List<VolunteerSignup> list = signupMapper.selectList(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(activityId != null, VolunteerSignup::getActivityId, activityId)
                .orderByDesc(VolunteerSignup::getCreatedAt));
        List<Map<String, Object>> res = new ArrayList<>();
        for (VolunteerSignup s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("activityId", s.getActivityId());
            m.put("signupName", s.getSignupName());
            m.put("phone", s.getPhone());
            m.put("status", s.getStatus());
            m.put("checkinAt", s.getCheckinAt());
            m.put("hoursMinutes", s.getHoursMinutes());
            m.put("certId", s.getCertId());
            m.put("createdAt", s.getCreatedAt());
            AppUser u = userMapper.selectById(s.getUserId());
            m.put("userName", u == null ? null : u.getNickname());
            m.put("userId", s.getUserId());
            Activity a = activityMapper.selectById(s.getActivityId());
            m.put("activityTitle", a == null ? null : a.getTitle());
            res.add(m);
        }
        return res;
    }

    // ---------- 后台活动管理 ----------
    public IPage<Activity> adminList(int current, int size) {
        IPage<Activity> page = activityMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Activity>().orderByDesc(Activity::getCreatedAt));
        for (Activity a : page.getRecords()) fillCoverIfBlank(a);
        return page;
    }

    public Activity save(Activity a) {
        if (a.getId() == null) {
            a.setSignedCount(0);
            if (a.getStatus() == null || a.getStatus().trim().isEmpty()) a.setStatus("PUBLISHED");
            activityMapper.insert(a);
        } else {
            activityMapper.updateById(a);
        }
        return a;
    }

    /** 只允许删除没有任何报名记录的活动，避免损坏志愿者与证书关联 */
    @Transactional
    public void delete(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(404, "活动不存在");
        Long count = signupMapper.selectCount(new LambdaQueryWrapper<VolunteerSignup>()
                .eq(VolunteerSignup::getActivityId, id));
        if (count != null && count > 0) {
            throw new BusinessException(400, "该活动已有 " + count + " 条报名记录，不能删除；可改为结束或取消活动");
        }
        activityMapper.deleteById(id);
    }

    /** AI 活动文案草稿（人工确认后发布） */
    public Map<String, Object> generateCopy(Map<String, Object> body) {
        String draft = aiService.generateCopy(str(body.get("topic")), str(body.get("tone")));
        AiAuditLog log = new AiAuditLog();
        log.setUserId(UserContext.get() == null ? null : UserContext.get().getUserId());
        log.setFeature("COPY"); log.setInputText(str(body.get("topic"))); log.setOutputText(draft); log.setDecision("PENDING");
        aiLogMapper.insert(log);
        Map<String, Object> res = new HashMap<>();
        res.put("draft", draft); res.put("hint", "AI 草稿需人工确认后发布，不会自动上线");
        return res;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
