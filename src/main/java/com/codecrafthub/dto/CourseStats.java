package com.codecrafthub.dto;

/**
 * The answer to {@code GET /api/courses/stats}.
 *
 * <p>A read-only summary computed on the fly from the stored courses - nothing here
 * is written to courses.json.
 *
 * <pre>{@code
 * {
 *   "total" : 4,
 *   "not_started" : 1,
 *   "in_progress" : 2,
 *   "completed" : 1,
 *   "completion_percentage" : 25.0,
 *   "overdue" : 1
 * }
 * }</pre>
 *
 * @param total                how many courses are tracked
 * @param notStarted           how many have status "Not Started"
 * @param inProgress           how many have status "In Progress"
 * @param completed            how many have status "Completed"
 * @param completionPercentage completed / total * 100, rounded to one decimal (0.0 when empty)
 * @param overdue              not yet completed but the target date has already passed
 */
public record CourseStats(
        long total,
        long notStarted,
        long inProgress,
        long completed,
        double completionPercentage,
        long overdue) {
}
