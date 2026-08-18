package com.splitopt.backend.expense.dto;

/**
 * 지출–일정 연결 변경 요청 (지출 수정(20)의 일정 부분만 떼어낸 것).
 *
 * <p>4주차에는 지출 등록·수정 폼에서 일정을 함께 고르기로 했지만, 폼이 이미 길어 일정 상세
 * 화면에서 연결하는 쪽으로 바뀌었다. 그 화면은 <b>일정만</b> 바꾸고 싶은데 수정(20)은 제목·금액·
 * 부담 내역까지 모두 요구한다. 일부만 보내면 나머지가 지워지므로, 연결만 바꾸는 길을 따로 둔다.
 *
 * @param scheduleId 연결할 일정. <b>{@code null}이거나 아예 없으면 연결을 해제</b>한다
 *                   (JSON에서 두 경우는 구분되지 않으므로 같은 뜻으로 정했다).
 */
public record ExpenseScheduleLinkRequest(Long scheduleId) {
}
