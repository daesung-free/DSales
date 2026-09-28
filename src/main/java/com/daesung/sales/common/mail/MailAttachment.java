package com.daesung.sales.common.mail;

/** 메일 첨부 하나. 파일명은 받는 사람이 그대로 보게 되므로 한글 그대로 쓴다. */
public record MailAttachment(String filename, byte[] content) {
}
