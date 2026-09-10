package com.mdlimonhossain.stumps

import com.mdlimonhossain.stumps.domain.model.PlayerRole
import com.mdlimonhossain.stumps.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun team_holdsAssignedPlayers() {
        val team = Team(id = "t1", name = "Dhaka Warriors", playerIds = listOf("p1", "p2"))
        assertEquals(2, team.playerIds.size)
    }

    @Test
    fun playerRole_hasFourValues() {
        assertEquals(4, PlayerRole.entries.size)
    }
}
