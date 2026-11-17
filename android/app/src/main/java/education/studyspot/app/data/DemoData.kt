package education.studyspot.app.data

import education.studyspot.app.model.Assignment
import education.studyspot.app.model.Connections
import education.studyspot.app.model.SchoolClass

/**
 * Local sample data for "demo" accounts, which never write to the server
 * (see DataRepository.isDemo). TODO: port ios/StudySpot/Logic/DemoData.swift.
 */
object DemoData {
    val classes: List<SchoolClass> = emptyList()
    val assignments: List<Assignment> = emptyList()
    val connections = Connections()
}
