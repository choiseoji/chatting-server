package com.chat.server.global.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebsocketHandler extends TextWebSocketHandler {

    // 채팅방 ID에 속한 세션을 알기 위한
    private final Map<String, Set<WebSocketSession>> chatRooms = new ConcurrentHashMap<>();

    // 해당 세션에 어떤 채팅방에 속해있는지 알기위한
    private final Map<WebSocketSession, String> sessionRoomMap = new ConcurrentHashMap<>();


    // 소켓 연결 확인
    // 프론트에서 /ws/conn으로 연결 요청하면
    // 1. HTTP -> WebSocket 핸드쉐이크 요청
    // 2. 연결 성공시 afterConnectionEstablished(WebSocketSession session) 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        log.info("{} 연결됨", session.getId());
//        session.sendMessage(new TextMessage("WebSocket 연결 완료"));
    }

    // 소켓 메시지 처리
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String payload = message.getPayload();
        log.info("payload {}", payload);

        // JSON 파싱
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(payload);
        String roomId = node.get("roomId").asText();
        String chatMessage = node.get("message").asText();

        // 방이 없으면 생성
        chatRooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        chatRooms.get(roomId).add(session);

        // 세션에 방 ID 등록
        sessionRoomMap.put(session, roomId);

        // 같은 방 세션에게만 전송
        for (WebSocketSession s : chatRooms.get(roomId)) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(chatMessage));
            }
        }
    }

    // 소켓 연결 종료
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        log.info("{} 연결 끊김", session.getId());

        String roomId = sessionRoomMap.get(session);
        if (roomId != null) {
            Set<WebSocketSession> sessions = chatRooms.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    chatRooms.remove(roomId);
                }
            }
        }
        sessionRoomMap.remove(session);
    }
}
