package top.foxball.nekomainsite.entity.jdbc

enum class ServerCategory { ACTIVITY, PERMANENT }
enum class ServerStatus { ONLINE, AVAILABLE, MAINTENANCE, OFFLINE }
enum class ActivityKind { WEEKLY, LONG_TERM, LIMITED }
enum class ActivityStatus { ACTIVE, UPCOMING, ONGOING, PAUSED }
enum class AnnouncementStatus { DRAFT, PUBLISHED, HIDDEN }
enum class ApplicationKind { SKIN, SERVER, DUTY }
enum class ModerationStatus { PENDING, ADOPTED, HIDDEN }
enum class FeedbackStatus { OPEN, PROCESSING, CLOSED }
enum class RegistrationStatus { PENDING, CONFIRMED, CANCELLED }
