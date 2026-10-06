package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.IntentRequest

/** Prompts for turning one spoken command into the JSON checked by `IntentSchema`. */
object IntentPrompt {
    /** Instructions shared by Nano and the cloud; [now] is local ISO date-time. */
    fun system(now: String, todoCategories: List<String>, exercises: List<String> = emptyList()): String = """
        You turn one spoken command into JSON. Reply with JSON only, no prose, exactly this shape:
        {"intents":[ ... ]} where each intent is one of:
        {"type":"set_alarm","time":"HH:mm","label":"","days":[]}   days: any of mon,tue,wed,thu,fri,sat,sun; empty = once
        {"type":"change_alarm","time":"HH:mm","label":null}
        {"type":"add_todo","items":[{"title":"Milk","category":"Shopping"}]}   category: one of ${todoCategories.joinToString()} or null
        {"type":"add_reminder","title":"Call mum","at":"yyyy-MM-ddTHH:mm"}   at: local time or null
        {"type":"log_expense","amount":340,"category":"Food","paid_with":"UPI","note":"Lunch","received":false}   amount in rupees
        {"type":"log_habit","name":"Walk"}
        {"type":"journal_note","text":"..."}
        {"type":"query_next"}
        {"type":"undo_last"}
        {"type":"log_sets","exercise":"Bench press","unit":null,"sets":[{"weight":62.5,"reps":8}]}   one entry per set; weight null if not said; unit kg or lb only if said${if (exercises.isEmpty()) "" else "; exercise: one of ${exercises.take(24).joinToString()}"}
        {"type":"plan_exercise","day":"2026-10-07","exercise":"Bench press","weight":60,"sets":3,"reps":8,"unit":null}   plans a lift on a day (ISO date from the current time); weight, sets, reps, unit may be null; one entry per lift
        {"type":"change_weight","exercise":"Bench press","weight":62.5,"unit":null}   changes today's planned weight
        {"type":"log_body_weight","weight":68.4,"unit":null}
        {"type":"next_workout"}
        The current local time is $now. Use 24-hour times. If the command is unclear, reply {"intents":[]}.
    """.trimIndent()

    /** One prompt string for Nano, or null when the transcript would not fit the model's input budget. */
    fun nano(request: IntentRequest): String? =
        "${system(request.now, request.context.todoCategories, request.context.exercises)}\n\nCommand: ${request.transcript}"
            .takeIf { it.length <= PromptLimits.NANO_MAX_PROMPT_CHARS }

    /** System instruction and user turn for the cloud. */
    fun cloudSystem(request: IntentRequest): String =
        system(request.now, request.context.todoCategories) + "\nTime zone: ${request.zone}."
}
