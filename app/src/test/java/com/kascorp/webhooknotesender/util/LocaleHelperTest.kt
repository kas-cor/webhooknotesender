package com.kascorp.webhooknotesender.util

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.just
import io.mockk.runs
import org.junit.Before
import org.junit.Test

class LocaleHelperTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns true
    }

    @Test
    fun `saveLanguage writes to SharedPreferences`() {
        // when
        LocaleHelper.saveLanguage(context, "ru")

        // then
        verify(exactly = 1) {
            editor.putString("language", "ru")
            editor.commit()
        }
    }

    @Test
    fun `saveLanguage overwrites previous language`() {
        // when
        LocaleHelper.saveLanguage(context, "ru")
        LocaleHelper.saveLanguage(context, "en")

        // then
        verify(exactly = 1) { editor.putString("language", "ru") }
        verify(exactly = 1) { editor.putString("language", "en") }
        verify(exactly = 2) { editor.commit() }
    }

    @Test
    fun `saveLanguage works with empty string`() {
        // when
        LocaleHelper.saveLanguage(context, "")

        // then
        verify(exactly = 1) { editor.putString("language", "") }
        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun `saveLanguage uses correct SharedPreferences name`() {
        // when
        LocaleHelper.saveLanguage(context, "ru")

        // then
        verify(exactly = 1) {
            context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        }
    }
}
