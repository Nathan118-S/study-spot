import { Router } from 'express';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';

const router = Router();
router.use(requireAuth);

// Entity configs: table name, owner column, and the set of writable columns.
// Mirrors the base44/entities/*.jsonc schemas.
const ENTITIES = {
  Assignment: {
    table: 'assignments',
    ownerColumn: 'created_by_id',
    columns: [
      'title', 'class_id', 'class_name', 'due_date', 'priority', 'type', 'notes',
      'description', 'source', 'external_id', 'completed', 'progress', 'subtasks',
      'points', 'score', 'reminder_sent', 'reminder_sent_hours',
    ],
    jsonColumns: ['subtasks'],
    defaults: { priority: 'medium', type: 'homework', source: 'manual', completed: false, progress: 0, points: 0 },
  },
  Class: {
    table: 'classes',
    ownerColumn: 'created_by_id',
    columns: ['name', 'color', 'teacher_name', 'source', 'external_id', 'best_streak'],
    defaults: { color: '#6366f1', source: 'manual', best_streak: 0 },
  },
  AssignmentTemplate: {
    table: 'assignment_templates',
    ownerColumn: 'created_by_id',
    columns: ['name', 'title', 'type', 'priority', 'points', 'notes'],
    defaults: { type: 'homework', priority: 'medium', points: 0 },
  },
  StudySession: {
    table: 'study_sessions',
    ownerColumn: 'created_by_id',
    columns: [
      'assignment_id', 'assignment_title', 'class_id', 'class_name',
      'duration_minutes', 'session_type', 'started_at', 'completed_at', 'note',
    ],
    defaults: { session_type: 'focus' },
  },
  Notification: {
    table: 'notifications',
    ownerColumn: 'user_id',
    columns: ['user_id', 'title', 'content', 'type', 'read', 'action_label', 'action_url'],
    defaults: { type: 'info', read: false },
  },
};

function shapeRow(row) {
  return { ...row, id: row.id, created_date: row.created_at, updated_date: row.updated_at };
}

function parseSort(sort, table) {
  if (!sort) return `${table === 'notifications' ? 'created_at' : 'created_at'} DESC`;
  const desc = sort.startsWith('-');
  const field = desc ? sort.slice(1) : sort;
  const column = field === 'created_date' ? 'created_at' : field === 'updated_date' ? 'updated_at' : field;
  const safe = /^[a-z_]+$/.test(column) ? column : 'created_at';
  return `${safe} ${desc ? 'DESC' : 'ASC'}`;
}

router.param('entity', (req, res, next, name) => {
  const config = ENTITIES[name];
  if (!config) return res.status(404).json({ error: 'Unknown entity' });
  req.entityConfig = config;
  next();
});

router.get('/:entity', async (req, res) => {
  try {
    const { table, ownerColumn } = req.entityConfig;
    const sort = parseSort(req.query.sort, table);
    const limit = Math.min(Number(req.query.limit) || 1000, 5000);
    const ownerId = ownerColumn === 'user_id' ? req.user.id : req.user.id;
    const result = await pool.query(
      `SELECT * FROM ${table} WHERE ${ownerColumn} = $1 ORDER BY ${sort} LIMIT $2`,
      [ownerId, limit]
    );
    res.json(result.rows.map(shapeRow));
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/:entity', async (req, res) => {
  try {
    const { table, ownerColumn, columns, jsonColumns = [], defaults = {} } = req.entityConfig;
    const body = { ...defaults, ...req.body };
    // Notification.user_id is caller-supplied in the SDK shape but must still
    // be owned by the caller; every other entity is owned by created_by_id.
    if (ownerColumn === 'created_by_id') body.created_by_id = req.user.id;
    else body.user_id = req.user.id;

    const cols = ['created_by_id', 'user_id'].includes(ownerColumn)
      ? [ownerColumn, ...columns.filter((c) => c !== ownerColumn)]
      : columns;
    const uniqueCols = [...new Set(cols)];

    const insertCols = [];
    const placeholders = [];
    const values = [];
    let i = 1;
    for (const col of uniqueCols) {
      if (!(col in body)) continue;
      insertCols.push(col);
      if (jsonColumns.includes(col)) {
        placeholders.push(`$${i++}::jsonb`);
        values.push(JSON.stringify(body[col]));
      } else {
        placeholders.push(`$${i++}`);
        values.push(body[col]);
      }
    }
    if (!insertCols.length) return res.status(400).json({ error: 'No fields to create' });

    const result = await pool.query(
      `INSERT INTO ${table} (${insertCols.join(', ')}) VALUES (${placeholders.join(', ')}) RETURNING *`,
      values
    );
    res.status(201).json(shapeRow(result.rows[0]));
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.patch('/:entity/:id', async (req, res) => {
  try {
    const { table, ownerColumn, columns, jsonColumns = [] } = req.entityConfig;
    const body = req.body || {};
    const sets = [];
    const values = [];
    let i = 1;
    for (const col of columns) {
      if (!(col in body)) continue;
      if (jsonColumns.includes(col)) {
        sets.push(`${col} = $${i++}::jsonb`);
        values.push(JSON.stringify(body[col]));
      } else {
        sets.push(`${col} = $${i++}`);
        values.push(body[col]);
      }
    }
    if (!sets.length) return res.status(400).json({ error: 'No fields to update' });
    sets.push('updated_at = now()');
    values.push(req.params.id, req.user.id);

    const result = await pool.query(
      `UPDATE ${table} SET ${sets.join(', ')} WHERE id = $${i++} AND ${ownerColumn} = $${i} RETURNING *`,
      values
    );
    if (!result.rows[0]) return res.status(404).json({ error: 'Not found' });
    res.json(shapeRow(result.rows[0]));
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.delete('/:entity/:id', async (req, res) => {
  try {
    const { table, ownerColumn } = req.entityConfig;
    const result = await pool.query(
      `DELETE FROM ${table} WHERE id = $1 AND ${ownerColumn} = $2 RETURNING id`,
      [req.params.id, req.user.id]
    );
    if (!result.rows[0]) return res.status(404).json({ error: 'Not found' });
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

export default router;
