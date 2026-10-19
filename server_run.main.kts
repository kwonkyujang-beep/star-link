#!/usr/bin/env kotlin

// 💡 맥북 자체 독립 터미널 환경에서 이 파일 단독으로 라이브러리를 끌어와 실행하기 위한 의존성 어노테이션
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("io.ktor:ktor-server-core-jvm:2.3.12")
@file:DependsOn("io.ktor:ktor-server-netty-jvm:2.3.12")
@file:DependsOn("io.ktor:ktor-server-websockets-jvm:2.3.12")

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

println("🚀 맥북 독립형 Ktor 실시간 백엔드 주문 서버 가동 준비 중...")

embeddedServer(Netty, port = 8888, host = "0.0.0.0") {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
    }
    
    // 연결된 주방 브라우저(dashboard) 세션 관리 풀
    val kitchenSessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

    routing {
        // 1. 주방 모니터 브라우저(kitchen_dashboard.html) 커넥션 파이프라인
        webSocket("/kitchen-stream") {
            kitchenSessions.add(this)
            println("🟢 [주방 대시보드 브라우저 웹소켓 연동 성공!]")
            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        println("주방 신호 수신: ${frame.readText()}")
                    }
                }
            } catch (e: Exception) {
                println("주방 브라우저 연결 종료: ${e.message}")
            } finally {
                kitchenSessions.remove(this)
            }
        }

        // 2. 안드로이드 손님 키오스크 앱 주문 요청 수신 엔드포인트
        webSocket("/place-order") {
            println("📱 [손님 스마트폰 단독 채널 커넥션 연결 확인]")
            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val rawOrderJson = frame.readText()
                        println("🚨 [실시간 주문 접수!] -> 주방 브라우저 전체 화면으로 즉각 멀티캐스팅 전송 개시")
                        
                        // 현재 대기중인 주방 브라우저 세션 전체에 즉각 실시간 패킷 밀어주기
                        kitchenSessions.forEach { session ->
                            try {
                                session.send(Frame.Text(rawOrderJson))
                            } catch (err: Exception) {
                                // 유실된 세션 스킵 처리
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("손님 채널 통신 예외: ${e.message}")
            }
        }
    }
}.start(wait = true)
