package com.example.nutritionplanner;

import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine
import com.embabel.agent.skills.script.ScriptLanguage

object ProcessSkillScriptExecutionEngineFactory {

    @JvmStatic
    @JvmOverloads
    fun create(
        timeoutSeconds: Long = 30,
        supportedLanguages : Set<ScriptLanguage> = setOf(ScriptLanguage.BASH),
    ): ProcessSkillScriptExecutionEngine =
        ProcessSkillScriptExecutionEngine()
}