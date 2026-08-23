// Demo role: shows fake, pre-populated content so anyone with the "demo" role
// can browse the app without real data or connected integrations.

export function isDemoUser(user) {
  return user?.role === 'demo';
}

export const demoConnections = { calendar: true, classroom: true, blackboard: true };

export function getDemoClasses() {
  return [
    { id: 'demo-c1', name: 'AP Calculus', color: '#6366f1', teacher_name: 'Mrs. Chen', source: 'google_classroom', external_id: 'd1' },
    { id: 'demo-c2', name: 'English Literature', color: '#ec4899', teacher_name: 'Mr. Patel', source: 'google_classroom', external_id: 'd2' },
    { id: 'demo-c3', name: 'Physics', color: '#10b981', teacher_name: 'Dr. Lee', source: 'blackboard', external_id: 'd3' },
    { id: 'demo-c4', name: 'US History', color: '#f59e0b', teacher_name: 'Ms. Garcia', source: 'manual' },
  ];
}

function dueAt(offsetDays, h = 9) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  d.setHours(h, 0, 0, 0);
  return d.toISOString();
}

export function getDemoAssignments() {
  return [
    { id: 'demo-a1', title: 'Problem Set 4', class_id: 'demo-c1', class_name: 'AP Calculus', due_date: dueAt(-2), priority: 'high', type: 'homework', source: 'google_classroom', external_id: 'a1', completed: false, progress: 40, points: 100, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a2', title: 'Essay Draft', class_id: 'demo-c2', class_name: 'English Literature', due_date: dueAt(0), priority: 'high', type: 'project', source: 'google_classroom', external_id: 'a2', completed: false, progress: 60, points: 50, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a3', title: 'Lab Report: Pendulums', class_id: 'demo-c3', class_name: 'Physics', due_date: dueAt(2), priority: 'medium', type: 'project', source: 'blackboard', external_id: 'a3', completed: false, progress: 20, points: 80, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a4', title: 'Chapter 5 Reading', class_id: 'demo-c4', class_name: 'US History', due_date: dueAt(4), priority: 'low', type: 'reading', source: 'manual', completed: false, progress: 0, points: 20, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a5', title: 'Quiz 3: Derivatives', class_id: 'demo-c1', class_name: 'AP Calculus', due_date: dueAt(1), priority: 'medium', type: 'quiz', source: 'google_classroom', external_id: 'a5', completed: false, progress: 0, points: 40, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a6', title: 'Vocab Worksheet', class_id: 'demo-c2', class_name: 'English Literature', due_date: dueAt(-5), priority: 'low', type: 'homework', source: 'google_classroom', external_id: 'a6', completed: true, progress: 100, points: 30, score: 28, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a7', title: 'Kinematics Problem Set', class_id: 'demo-c3', class_name: 'Physics', due_date: dueAt(-1), priority: 'high', type: 'homework', source: 'blackboard', external_id: 'a7', completed: true, progress: 100, points: 50, score: 47, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
    { id: 'demo-a8', title: 'Unit 2 Test', class_id: 'demo-c1', class_name: 'AP Calculus', due_date: dueAt(7), priority: 'high', type: 'test', source: 'google_classroom', external_id: 'a8', completed: false, progress: 0, points: 100, score: 0, notes: '', subtasks: [], reminder_sent: false, reminder_sent_hours: [] },
  ];
}

// Precomputed streaks with fake (inflated) numbers, independent of the demo
// assignments above.
export function getDemoStreaks() {
  const classes = getDemoClasses();
  const data = [
    { streak: 14, status: 'active', total: 8, completed: 8, verified: true },
    { streak: 9, status: 'active', total: 6, completed: 6, verified: true },
    { streak: 5, status: 'atrisk', total: 7, completed: 6, verified: true },
    { streak: 0, status: 'broken', total: 5, completed: 3, verified: false },
  ];
  return classes.map((c, i) => ({ class: c, ...data[i] }));
}

export const demoStreakSummary = { active: 2, atRisk: 1, broken: 1, best: 14, total: 4 };