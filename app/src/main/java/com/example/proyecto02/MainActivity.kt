package com.example.proyecto02

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ==========================================
// 1. CAPA DE DATOS: ENTIDAD
// ==========================================
@Entity(tableName = "tabla_tareas")
data class Tarea(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val estaCompletada: Boolean = false
)

// ==========================================
// 2. DATA ACCESS OBJECT (Dao) - Sin suspend por compatibilidad KSP
// ==========================================
@Dao
interface TareaDao {
    @Query("SELECT * FROM tabla_tareas ORDER BY id DESC")
    fun obtenerTodas(): Flow<List<Tarea>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertar(tarea: Tarea)

    @Update
    fun actualizar(tarea: Tarea)

    @Delete
    fun eliminar(tarea: Tarea)

    @Query("DELETE FROM tabla_tareas WHERE estaCompletada = 1")
    fun eliminarCompletadas()
}

// ==========================================
// 3. BASE DE DATOS LOCAL
// ==========================================
@Database(entities = [Tarea::class], version = 1, exportSchema = false)
abstract class TareaDatabase : RoomDatabase() {
    abstract fun tareaDao(): TareaDao

    companion object {
        @Volatile
        private var INSTANCE: TareaDatabase? = null

        fun getDatabase(context: Context): TareaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TareaDatabase::class.java,
                    "tareas_db"
                )
                    .allowMainThreadQueries() // Solución al bug de KSP en Kotlin 2+
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 4. VIEWMODEL
// ==========================================
enum class FiltroTareas { TODAS, PENDIENTES, COMPLETADAS }

class TareaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = TareaDatabase.getDatabase(application).tareaDao()

    var filtroActual by mutableStateOf(FiltroTareas.TODAS)
        private set

    // Flujo reactivo que se filtra automáticamente según el estado seleccionado
    val tareasFiltradas = dao.obtenerTodas().map { lista ->
        when (filtroActual) {
            FiltroTareas.TODAS -> lista
            FiltroTareas.PENDIENTES -> lista.filter { !it.estaCompletada }
            FiltroTareas.COMPLETADAS -> lista.filter { it.estaCompletada }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cambiarFiltro(nuevoFiltro: FiltroTareas) { filtroActual = nuevoFiltro }
    fun agregarTarea(titulo: String) { dao.insertar(Tarea(titulo = titulo)) }
    fun cambiarEstado(tarea: Tarea, completada: Boolean) { dao.actualizar(tarea.copy(estaCompletada = completada)) }
    fun borrarTarea(tarea: Tarea) { dao.eliminar(tarea) }
    fun limpiarCompletadas() { dao.eliminarCompletadas() }
}

// ==========================================
// 5. INTERFAZ DE USUARIO (Compose)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppGestorTareas() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGestorTareas(viewModel: TareaViewModel = viewModel()) {
    val tareas by viewModel.tareasFiltradas.collectAsState()
    var mostrarDialogo by remember { mutableStateOf(false) }
    var menuFiltroExpandido by remember { mutableStateOf(false) }
    var menuOpcionesExpandido by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tareas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Menú de Filtro
                    IconButton(onClick = { menuFiltroExpandido = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filtrar")
                    }
                    DropdownMenu(
                        expanded = menuFiltroExpandido,
                        onDismissRequest = { menuFiltroExpandido = false }
                    ) {
                        DropdownMenuItem(text = { Text("Todas") }, onClick = { viewModel.cambiarFiltro(FiltroTareas.TODAS); menuFiltroExpandido = false })
                        DropdownMenuItem(text = { Text("Pendientes") }, onClick = { viewModel.cambiarFiltro(FiltroTareas.PENDIENTES); menuFiltroExpandido = false })
                        DropdownMenuItem(text = { Text("Completadas") }, onClick = { viewModel.cambiarFiltro(FiltroTareas.COMPLETADAS); menuFiltroExpandido = false })
                    }

                    // Menú de Opciones Extra
                    IconButton(onClick = { menuOpcionesExpandido = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = menuOpcionesExpandido,
                        onDismissRequest = { menuOpcionesExpandido = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Borrar completadas") },
                            onClick = { viewModel.limpiarCompletadas(); menuOpcionesExpandido = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarDialogo = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar Tarea")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tareas) { tarea ->
                ItemTarea(
                    tarea = tarea,
                    onCheckedChange = { viewModel.cambiarEstado(tarea, it) },
                    onDelete = { viewModel.borrarTarea(tarea) }
                )
            }
        }

        if (mostrarDialogo) {
            DialogoAgregarTarea(
                onDismiss = { mostrarDialogo = false },
                onConfirm = { titulo ->
                    viewModel.agregarTarea(titulo)
                    mostrarDialogo = false
                }
            )
        }
    }
}

@Composable
fun ItemTarea(tarea: Tarea, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = tarea.estaCompletada, onCheckedChange = onCheckedChange)
            Text(
                text = tarea.titulo,
                fontSize = 18.sp,
                textDecoration = if (tarea.estaCompletada) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DialogoAgregarTarea(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Tarea") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Descripción") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (texto.isNotBlank()) onConfirm(texto) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}