package `fun`.utf8.nekoprojectbackend.service

import `fun`.utf8.nekoprojectbackend.config.MailProperties
import `fun`.utf8.nekoprojectbackend.handlder.EmailSendFailedException
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

/**
 * 邮件发送服务：基于 JavaMailSender 发 HTML 验证码邮件。
 *
 * 通用验证码模板 [buildVerificationHtml] 适配所有场景（注册 / 改密码 / 找回密码 / 邮箱登录），
 * 仅场景标题与说明文案随 [VerificationCodeService.Scene] 变化。
 */
@Service
class MailService(
    private val mailSender: JavaMailSender,
    private val props: MailProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 发送验证码邮件。from 取 neko.mail.verification.from（默认回退到 MAIL_USERNAME）。 */
    fun sendVerificationCode(toEmail: String, code: String, scene: VerificationCodeService.Scene) {
        val from = props.from
        if (from.isBlank()) {
            log.error("发件人地址未配置（neko.mail.verification.from / MAIL_FROM / MAIL_USERNAME），无法发送邮件")
            throw EmailSendFailedException()
        }
        val subject = "${props.subjectPrefix} - ${scene.description}验证码"
        val html = buildVerificationHtml(code, scene)
        val mime: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mime, false, Charsets.UTF_8.name())
        try {
            helper.setFrom(from)
            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(html, true) // true = HTML
            mailSender.send(mime)
            log.info("验证码邮件已发送：to={}, scene={}", toEmail, scene)
        } catch (e: Exception) {
            log.error("验证码邮件发送失败：to={}, scene={}, err={}", toEmail, scene, e.message, e)
            throw EmailSendFailedException()
        }
    }

    /**
     * 通用验证码 HTML 模板：暖色卡片风格，与前端 Minecraft 主题一致。
     * 所有场景共用，仅 [sceneDescription] / [sceneTitle] 随场景变化。
     */
    private fun buildVerificationHtml(code: String, scene: VerificationCodeService.Scene): String {
        val minutes = (props.ttlSeconds / 60).coerceAtLeast(1)
        val sceneTitle = scene.description
        val sceneHint = sceneHint(scene)
        return """
            <div style="max-width:480px;margin:0 auto;font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#2d2418;">
              <div style="background:#6b8f32;color:#fffbe4;padding:18px 22px;border-radius:10px 10px 0 0;font-weight:900;font-size:18px;">
                ${escape(props.subjectPrefix)}
              </div>
              <div style="border:2px solid #5a3a21;border-top:0;border-radius:0 0 10px 10px;padding:24px 22px;background:#fffdf3;">
                <h2 style="margin:0 0 8px;color:#2d2418;">${escape(sceneTitle)}验证码</h2>
                <p style="margin:0 0 16px;color:#60462b;line-height:1.7;">$sceneHint</p>
                <div style="text-align:center;margin:18px 0;">
                  <span style="display:inline-block;letter-spacing:8px;font-size:32px;font-weight:900;color:#5a3a21;background:#ffdf7e;border:2px solid #5a3a21;border-radius:8px;padding:12px 20px;">
                    ${escape(code)}
                  </span>
                </div>
                <p style="margin:0;color:#795b36;font-size:13px;line-height:1.7;">
                  验证码 ${minutes} 分钟内有效，请勿向他人泄露。若非本人操作，请忽略本邮件。
                </p>
              </div>
              <p style="text-align:center;color:#9b8665;font-size:12px;margin-top:14px;">
                此邮件由系统自动发送，请勿直接回复。
              </p>
            </div>
        """.trimIndent()
    }

    /** 各场景的说明文案。 */
    private fun sceneHint(scene: VerificationCodeService.Scene): String =
        when (scene) {
            VerificationCodeService.Scene.REGISTER -> "你正在注册账号，请使用下方验证码完成验证。"
            VerificationCodeService.Scene.CHANGE_PASSWORD -> "你正在修改账号密码，请使用下方验证码进行身份确认。"
            VerificationCodeService.Scene.RESET_PASSWORD -> "你正在找回账号密码，请使用下方验证码重置密码。"
            VerificationCodeService.Scene.EMAIL_LOGIN -> "你正在进行邮箱验证登录，请使用下方验证码完成登录。"
        }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
