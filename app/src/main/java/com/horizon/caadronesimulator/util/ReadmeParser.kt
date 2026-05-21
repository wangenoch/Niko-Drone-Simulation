package com.horizon.caadronesimulator.util

import android.util.Log

data class UpdateFeature(
    val title: String,
    val items: List<String>
)

data class ReadmeUpdateData(
    val fullTitle: String,
    val intro: String,
    val features: List<UpdateFeature>,
    val knownIssues: List<String>,
    val specialThanks: List<String>
)

/**
 * [v1.3.9] README.md 自動解析工具 (高容錯增強版)
 */
object ReadmeParser {
    
    fun parseUpdateNotice(): ReadmeUpdateData? {
        return try {
            val fullText = com.horizon.caadronesimulator.generated.ReleaseContent.RAW_MARKDOWN

            // 1. 定位主版本區塊
            // 尋找以 "## 🚀 v" 開頭的整行
            val mainHeaderRegex = "^## 🚀 v.*$".toRegex(RegexOption.MULTILINE)
            val headerMatch = mainHeaderRegex.find(fullText) ?: return null
            val fullTitle = headerMatch.value.removePrefix("##").trim()
            
            // 擷取該區塊直到下一個 "---" 或文件結尾
            val startIndex = headerMatch.range.last + 1
            val separatorIndex = fullText.indexOf("---", startIndex)
            val updateBlock = if (separatorIndex != -1) {
                fullText.substring(startIndex, separatorIndex)
            } else {
                fullText.substring(startIndex)
            }

            // 2. 擷取前言 (Intro)
            // 第一個 "###" 出現之前的內容
            val firstSectionIndex = updateBlock.indexOf("###")
            val intro = if (firstSectionIndex != -1) {
                updateBlock.substring(0, firstSectionIndex).trim()
            } else ""

            // 3. 解析所有三級標題區塊 (###)
            val sectionRegex = "### (.*)".toRegex()
            val features = mutableListOf<UpdateFeature>()
            val knownIssues = mutableListOf<String>()
            val specialThanks = mutableListOf<String>()
            
            val sectionMatches = sectionRegex.findAll(updateBlock).toList()
            sectionMatches.forEachIndexed { index, match ->
                val sectionTitle = match.groupValues[1].trim()
                val nextStart = if (index < sectionMatches.size - 1) sectionMatches[index + 1].range.first else updateBlock.length
                val sectionContent = updateBlock.substring(match.range.last + 1, nextStart)
                
                val items = sectionContent.lines()
                    .filter { it.trim().startsWith("*") }
                    .map { it.trim().removePrefix("*").replace("\\*\\*".toRegex(), "").trim() }

                when {
                    sectionTitle.contains("Known Issues") || sectionTitle.contains("已知問題") -> {
                        knownIssues.addAll(items)
                    }
                    sectionTitle.contains("Special Thanks") || sectionTitle.contains("特別感謝") -> {
                        specialThanks.addAll(items)
                    }
                    else -> {
                        if (items.isNotEmpty()) {
                            features.add(UpdateFeature(sectionTitle, items))
                        }
                    }
                }
            }

            ReadmeUpdateData(fullTitle, intro, features, knownIssues, specialThanks)
        } catch (e: Exception) {
            Log.e("ReadmeParser", "Failed to parse README: ${e.message}")
            null
        }
    }
}
