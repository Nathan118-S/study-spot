// A ready-to-send email asking a school's IT team to approve Study Spot, for
// when the school blocks connecting third-party apps. Opened in the user's
// mail app with a mailto: link; they add the recipient and the school's name.
// (The iOS app has the same text in ios/StudySpot/Views/Settings/SchoolITEmail.swift.)

export const SCHOOL_PLACEHOLDER = '[School name]';
export const SCHOOLS_URL = 'https://studyspot.education/schools';

const DETAILS = {
  google: {
    what: 'my school Google account (Google Calendar and Google Classroom)',
    access:
      'Study Spot only asks for read-only access to Google Calendar and to my Google Classroom courses and coursework ' +
      "(and, if I choose, marking my own Classroom work as turned in). It doesn't ask for access to Gmail or Drive.",
    admin:
      'In the Google Admin console this is under Security → Access and data control → API controls → App access control, ' +
      'where Study Spot can be marked as trusted.',
  },
  blackboard: {
    what: 'my Blackboard account',
    access:
      "Study Spot uses Blackboard's REST API to read my courses and the due dates in the gradebook. It doesn't change anything in Blackboard.",
    admin: 'In Blackboard this is done by an administrator under REST API Integrations.',
  },
};

/** The user's name for signing the email. */
export function senderName(user) {
  const n = (user?.full_name || user?.name || '').trim();
  if (n) return n;
  const u = (user?.username || '').split('@')[0];
  return u || 'A student';
}

/** mailto: URL with the subject and body filled in. service: 'google' | 'blackboard' | 'both' */
export function schoolItEmailUrl(user, service = 'both') {
  const name = senderName(user);
  const keys = service === 'both' ? ['google', 'blackboard'] : [service];
  const what = keys.map((k) => DETAILS[k].what).join(' and ');
  const subject = `Request to approve Study Spot at ${SCHOOL_PLACEHOLDER}`;
  const body = [
    `Hello ${SCHOOL_PLACEHOLDER} IT team,`,
    '',
    `My name is ${name}, and I'm a student at ${SCHOOL_PLACEHOLDER}. I use Study Spot (https://studyspot.education), ` +
      'a study planner that brings my assignments and due dates into one place so I can keep up with my schoolwork.',
    '',
    `When I try to connect ${what}, it looks like the school has to approve third-party apps first. ` +
      'Could you please approve Study Spot so students can connect it?',
    '',
    ...keys.flatMap((k) => [DETAILS[k].access, DETAILS[k].admin, '']),
    `Everything your team needs to review and approve it, including the IDs to allow and step-by-step instructions, is at ${SCHOOLS_URL}`,
    '',
    'Thank you for your help!',
    '',
    name,
  ].join('\n');
  return `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}
