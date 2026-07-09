package `fun`.utf8.nekoprojectbackend.datasource.jdbc

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JoinApplicationRepository : JpaRepository<JoinApplication, Int> {
    fun findByObjectItemId(objectItemId: Int): List<JoinApplication>

    fun findByObjectItemIdAndStatus(objectItemId: Int, status: JoinApplicationStatus): List<JoinApplication>
}
