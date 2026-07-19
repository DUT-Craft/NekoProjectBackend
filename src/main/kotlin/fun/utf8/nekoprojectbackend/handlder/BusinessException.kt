package `fun`.utf8.nekoprojectbackend.handlder

import org.springframework.http.HttpStatus

/** 业务异常基类，携带 HTTP 状态码；子类对应各类业务错误（未找到、未授权、参数错误等）。 */
open class BusinessException(
    val status: HttpStatus,
    override val message: String
) : RuntimeException(message) {
    val code: Int = status.value()
}

class UserNotFoundException(
    message: String = "用户不存在"
) : BusinessException(HttpStatus.NOT_FOUND, message)

class UserAlreadyExistsException(
    message: String = "用户已存在"
) : BusinessException(HttpStatus.CONFLICT, message)

class UsernameOrPasswordErrorException(
    message: String = "用户名或密码错误"
) : BusinessException(HttpStatus.UNAUTHORIZED, message)

class UserDisabledException(
    message: String = "用户被禁用"
) : BusinessException(HttpStatus.FORBIDDEN, message)

class UnauthorizedException(
    message: String = "未授权"
) : BusinessException(HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(
    message: String = "禁止访问"
) : BusinessException(HttpStatus.FORBIDDEN, message)

class ResourceNotFoundException(
    message: String = "资源不存在"
) : BusinessException(HttpStatus.NOT_FOUND, message)

class ConflictException(
    message: String = "资源冲突"
) : BusinessException(HttpStatus.CONFLICT, message)

class ParamErrorException(
    message: String = "参数错误"
) : BusinessException(HttpStatus.BAD_REQUEST, message)

class VerificationCodeInvalidException(
    message: String = "验证码错误或已过期"
) : BusinessException(HttpStatus.BAD_REQUEST, message)

class EmailSendFailedException(
    message: String = "验证码邮件发送失败，请稍后重试"
) : BusinessException(HttpStatus.SERVICE_UNAVAILABLE, message)

class TokenInvalidException(
    message: String = "Token 无效"
) : BusinessException(HttpStatus.UNAUTHORIZED, message)

class TokenExpiredException(
    message: String = "Token 过期"
) : BusinessException(HttpStatus.UNAUTHORIZED, message)

class TokenForbiddenException(
    message: String = "Token 校验失败"
) : BusinessException(HttpStatus.FORBIDDEN, message)
