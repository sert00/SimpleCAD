package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CadRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProjects(userId: String = requireUserId()): Flow<List<CadProject>> = flow {
        val path = "cad_projects"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CadProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getUserProjects(userId: String = requireUserId()): Result<List<CadProject>> {
        val path = "cad_projects"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CadProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getProjectById(projectId: String): Result<CadProject> {
        val path = "cad_projects/$projectId"
        return try {
            val doc = db.collection("cad_projects").document(projectId).get().await()
            val project = doc.toObject(CadProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("Project not found: $projectId")
            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createProject(
        name: String,
        templateType: String,
        description: String,
        components: List<CadComponent>,
        explodeFactor: Float,
        multiObject3mf: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val projectId = UUID.randomUUID().toString()
        val docRef = db.collection("cad_projects").document(projectId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.take(120).ifBlank { "Progetto SimpleCAD" },
            "templateType" to templateType.take(64).ifBlank { "CUSTOM_SCRATCH" },
            "description" to description.take(1000),
            "componentsJson" to CadComponent.serializeList(components),
            "explodeFactor" to explodeFactor.toDouble().coerceIn(0.0, 1.0),
            "multiObject3mf" to multiObject3mf,
            "partCount" to components.size.toLong().coerceIn(0L, 500L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProject(
        projectId: String,
        name: String,
        templateType: String,
        description: String,
        components: List<CadComponent>,
        explodeFactor: Float,
        multiObject3mf: Boolean
    ): Result<Unit> {
        requireUserId()
        require(projectId.isNotBlank()) { "Project ID must not be blank" }
        val docRef = db.collection("cad_projects").document(projectId)
        val updates = mapOf(
            "name" to name.take(120).ifBlank { "Progetto SimpleCAD" },
            "templateType" to templateType.take(64).ifBlank { "CUSTOM_SCRATCH" },
            "description" to description.take(1000),
            "componentsJson" to CadComponent.serializeList(components),
            "explodeFactor" to explodeFactor.toDouble().coerceIn(0.0, 1.0),
            "multiObject3mf" to multiObject3mf,
            "partCount" to components.size.toLong().coerceIn(0L, 500L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("cad_projects").document(projectId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observePrinterProfiles(userId: String = requireUserId()): Flow<List<PrinterProfile>> = flow {
        val path = "printer_profiles"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(PrinterProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createPrinterProfile(
        name: String,
        protocol: String,
        endpointUrl: String,
        apiKey: String,
        bedWidthMm: Int,
        bedDepthMm: Int,
        nozzleMm: Float
    ): Result<String> {
        val uid = requireUserId()
        val printerId = UUID.randomUUID().toString()
        val docRef = db.collection("printer_profiles").document(printerId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.take(100).ifBlank { "Stampante 3D" },
            "protocol" to protocol,
            "endpointUrl" to endpointUrl.take(500).ifBlank { "http://192.168.1.100:7125" },
            "apiKey" to apiKey.take(256),
            "bedWidthMm" to bedWidthMm.toLong().coerceIn(50L, 1000L),
            "bedDepthMm" to bedDepthMm.toLong().coerceIn(50L, 1000L),
            "nozzleMm" to nozzleMm.toDouble().coerceIn(0.1, 2.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(printerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePrinterProfile(printerId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("printer_profiles").document(printerId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
