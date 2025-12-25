package com.songwh.bosprojectinit.utils

import com.songwh.bosprojectinit.ui.components.GitRepoSection
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleRegexTest {
    @Test
    fun testValidUrls() {
        assertTrue(GitRepoSection.isValidGitUrl("https://github.com/user/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("http://github.com/user/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("git@github.com:user/repo.git"))
    }
}