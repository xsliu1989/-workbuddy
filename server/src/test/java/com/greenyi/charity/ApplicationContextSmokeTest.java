package com.greenyi.charity;

import com.greenyi.charity.controller.*;
import com.greenyi.charity.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 冒烟测试：验证 Spring 上下文能完成所有 Bean 装配
 * 使用 H2 内存库，不依赖真实 MySQL
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextSmokeTest {

    @Autowired private AuthController authController;
    @Autowired private DonationController donationController;
    @Autowired private ShopController shopController;
    @Autowired private ClaimController claimController;
    @Autowired private ActivityController activityController;
    @Autowired private ContentController contentController;
    @Autowired private UserController userController;
    @Autowired private CertificateController certificateController;
    @Autowired private AdminController adminController;

    @Autowired private DonationService donationService;
    @Autowired private PointService pointService;
    @Autowired private HonorService honorService;
    @Autowired private CertificateService certificateService;
    @Autowired private OrderService orderService;
    @Autowired private ClaimService claimService;
    @Autowired private ActivityService activityService;
    @Autowired private AiServiceFacade aiServiceFacade;

    @Test
    void allBeansWired() {
        assertNotNull(authController);
        assertNotNull(donationController);
        assertNotNull(shopController);
        assertNotNull(claimController);
        assertNotNull(activityController);
        assertNotNull(contentController);
        assertNotNull(userController);
        assertNotNull(certificateController);
        assertNotNull(adminController);
        assertNotNull(donationService);
        assertNotNull(pointService);
        assertNotNull(honorService);
        assertNotNull(certificateService);
        assertNotNull(orderService);
        assertNotNull(claimService);
        assertNotNull(activityService);
        assertNotNull(aiServiceFacade);
    }
}
