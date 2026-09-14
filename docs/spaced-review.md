# Offline retrieval and spaced review

The Android v0.3 pilot adds **Review decisions over time** to the learning home. It uses assessment evidence to choose questions, asks for a response before revealing feedback, offers an explicit “I’m not sure” option, gives the reviewed explanation and invites a short self-explanation.

## What is implemented

- Latest finished assessment per module supplies the evidence. Guided practice does not create an assessment-based review schedule.
- A missed answered decision is initially due immediately. A correct answered decision is due one day after that assessment. Unanswered decisions are not treated as passed.
- Successful reviews use pilot intervals of 1, 3, 7 and then 14 days; an incorrect or uncertain response returns after 10 minutes. Late reviews do not incur a penalty. These intervals are product hypotheses, not a proven optimal schedule for industrial skills.
- The schedule, source attempt, response, correctness and successful-review count are saved locally in a separate SQLite table. Activity recreation preserves displayed feedback. Existing assessment histories and credentials are untouched.
- Questions must match their archived source content before old answers seed the current schedule. Changed questions require fresh assessment evidence. A newer assessment resets the derived schedule for that question.
- The screen offers at most 20 due decisions at once, with the next review date when caught up. It works in English/Hindi and without a network connection.

## Boundaries

This is decision retrieval in the phone interface. It is not yet spatial component testing, a validated adaptive learning algorithm, an automatic notification system, or practical competence assessment. Review history is local and is intentionally outside the existing assessment export/credential pipeline. Device time controls local due dates; it is not trusted certification evidence.

[AR learning evidence](ar-learning-evidence.md) describes the research basis, its transfer limits, six further interactions and a delayed practical-transfer evaluation. Next: guided component inspection that fades labels, followed by independent identification and reviewed scenario variation. Do not confuse visual presence or same-session recognition with durable safe performance.
