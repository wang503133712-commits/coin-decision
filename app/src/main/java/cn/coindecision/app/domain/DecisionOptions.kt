package cn.coindecision.app.domain

data class DecisionOptions(val a: String, val b: String) {
    companion object {
        private val modal = Regex("要不要|是否|该不该|应不应该|应该不应该|需不需要|可不可以|能不能|有没有必要")
        private val repeatedVerb = Regex("([去买吃喝做看选用穿住学读换带租借卖])不\\1")
        private val action = Regex("去|买|吃|喝|做|看|选|用|穿|住|学|读|换|带|租|借|卖|参加|接受|继续|放弃|留下|离开|辞职|请假|在|待|不|别")

        // shortcut: 只识别明确的中文二选一句式；开放式或多项问题由用户补充两种选择。
        fun infer(matter: String): DecisionOptions? {
            val text = matter.trim().trimEnd('？', '?', '。', '.', '！', '!', '吗', '呢', '吧').trim()
                .replace(Regex("^(?:请问|我想知道|我在纠结|我想问)(?:一下)?[，,：:]?"), "")
            if (text.isBlank() || text.any { it in "\n\r？?；;，,。" } || text.contains("或者")) return null
            val choices = text.split("还是")
            if (choices.size == 2) {
                val a = choices[0].trim().removePrefix("是").trim()
                val b = choices[1].trim()
                if (modal.containsMatchIn(a) || repeatedVerb.containsMatchIn(a)) return null
                return checked(a, b)
            }
            if (choices.size != 1) return null
            val matches = modal.findAll(text).toList() + repeatedVerb.findAll(text).toList()
            if (matches.size == 1) {
                val match = matches.single()
                val prefix = text.substring(0, match.range.first)
                val suffix = text.substring(match.range.last + 1).trim()
                val verb = if (match.groupValues.size > 1) match.groupValues[1] else ""
                val positive = verb + suffix
                if (positive.isBlank() || modal.containsMatchIn(positive)) return null
                val opposite = when {
                    positive.startsWith("不") -> positive.removePrefix("不")
                    positive.startsWith("别") -> positive.removePrefix("别")
                    positive.startsWith("没") || positive.startsWith("无") -> return null
                    else -> "不$positive"
                }
                return checked(prefix + positive, prefix + opposite)
            }
            if (matches.isNotEmpty() || !matter.trim().trimEnd('？', '?', '。', '.').endsWith("吗")) return null
            val verb = action.find(text) ?: return null
            val prefix = text.substring(0, verb.range.first).removeSuffix("应该").removeSuffix("需要").removeSuffix("可以").removeSuffix("要")
            val positive = text.substring(verb.range.first)
            val opposite = if (positive.startsWith("不")) positive.removePrefix("不") else "不$positive"
            return checked(prefix + positive, prefix + opposite)
        }

        private fun checked(a: String, b: String): DecisionOptions? =
            if (a.isNotBlank() && b.isNotBlank() && a != b && a.length <= 120 && b.length <= 120) DecisionOptions(a, b) else null
    }
}
