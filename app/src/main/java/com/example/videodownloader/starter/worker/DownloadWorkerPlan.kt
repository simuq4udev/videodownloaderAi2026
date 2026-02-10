package com.example.videodownloader.starter.worker

/**
 * Starter plan for WorkManager based downloads.
 *
 * 1) Create OneTimeWorkRequest with URL + formatId
 * 2) Run foreground notification with progress
 * 3) Stream bytes into MediaStore Uri
 * 4) Update local DB status
 */
object DownloadWorkerPlan
