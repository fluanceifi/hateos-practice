package com.example.smu_club.util.discord;

import com.example.smu_club.util.discord.annotation.DiscordAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TempAopTestController {

    @GetMapping("/api/v1/discord-aop-test")
    @DiscordAlert("디스코드 AOP 테스트")
    public void test(){
        throw new RuntimeException("디스코드 AOP 테스트용 예외 발생");

    }
}
