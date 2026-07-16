package com.autodoctor.aipro

import android.app.Application
import com.autodoctor.aipro.core.knowledge.KnowledgeRepository
import com.autodoctor.aipro.data.local.AutoDoctorDatabase

class AutoDoctorApp : Application() {
    val database: AutoDoctorDatabase by lazy { AutoDoctorDatabase.create(this) }
    val knowledgeRepository: KnowledgeRepository by lazy { KnowledgeRepository(this) }
}
