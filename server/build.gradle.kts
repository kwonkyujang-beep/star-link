plugins {
    kotlin("jvm")
    application
}

group = "com.example.star_link"
version = "1.0.0"

// 맥북의 다양한 환경에서도 충돌 없이 실행되도록 JVM 11로 고정합니다.
kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-websockets:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // 심장박동(Heartbeat) 통신을 위한 클라이언트 라이브러리 추가
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")

    // JSON 데이터 처리를 위한 직렬화 라이브러리 추가
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 정식 로컬 DB 처리를 위한 Exposed 및 SQLite 라이브러리 추가
    val exposedVersion = "0.41.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
}

application {
    mainClass.set("com.example.star_link.server.ServerMainKt")
}

tasks.withType<JavaExec> {
    environment(System.getenv())
}


