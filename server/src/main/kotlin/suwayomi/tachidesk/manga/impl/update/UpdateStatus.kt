package suwayomi.tachidesk.manga.impl.update

import com.fasterxml.jackson.annotation.JsonIgnore
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

data class UpdateStatus(
    val categoryStatusMap: Map<CategoryUpdateStatus, List<CategoryDataClass>> = emptyMap(),
    val mangaStatusMap: Map<JobStatus, List<MangaDataClass>> = emptyMap(),
    val running: Boolean = false,
    @JsonIgnore
    val numberOfJobs: Int = 0,
) {
    constructor(
        categories: Map<CategoryUpdateStatus, List<CategoryDataClass>>,
        jobs: List<UpdateJob>,
        skippedMangas: List<MangaDataClass>,
        running: Boolean,
    ) : this(
        categories,
        mangaStatusMap =
            jobs
                .groupBy { it.status }
                .mapValues { entry ->
                    entry.value.map { it.manga }
                }.plus(Pair(JobStatus.SKIPPED, skippedMangas)),
        running = running,
        numberOfJobs = jobs.size,
    )
}

data class UpdateUpdates(
    val isRunning: Boolean = false,
    val categoryUpdates: List<CategoryUpdateJob>,
    val mangaUpdates: List<UpdateJob>,
    val totalJobs: Int,
    val finishedJobs: Int,
    val skippedCategoriesCount: Int,
    val skippedMangasCount: Int,
    val initial: UpdateUpdates?,
)
