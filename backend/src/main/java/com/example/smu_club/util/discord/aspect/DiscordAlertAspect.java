package com.example.smu_club.util.discord.aspect;

import com.example.smu_club.util.discord.annotation.DiscordAlert;
import com.example.smu_club.util.discord.service.DiscordAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

//@Aspect는 해당 클래스가 공통 관심사(Cross-cutting Concerns)를 정의한 '메타데이터(규칙서)'임을 나타내며,
//스프링은 이를 참조하여 프록시(Proxy) 객체를 생성하고
//내부적인 메서드 호출 가로채기(Interception) 로직을 통해 부가 기능을 주입한다.
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordAlertAspect {

    private final DiscordAlertService discordAlertService;

    @AfterThrowing(pointcut = "@annotation(discordAlert)", throwing = "ex")
    public void handleException(JoinPoint jp, DiscordAlert discordAlert, Exception ex) {
        //디스코드 알림 서비스 호출
        String methodName = jp.getSignature().getName();
        String className = jp.getSignature().getName();
        String alertTitle = discordAlert.value();

        String message = String.format(
                """
                        **[%s]**
                        - 클래스: %s
                        - 메서드: %s
                        - 메시지: %s""",
                alertTitle, className, methodName, ex.getMessage()
        );

        log.info("Discord Alert AOP가 에러를 감지했습니다.\n디스코드로 알람을 전송합니다.");
        discordAlertService.sendErrorAlert(message);

    }

}
