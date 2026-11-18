// Replace the bracketed placeholders with the real hospital details.
export const hospital = {
  name: 'Haven Hospital',
  erNumber: '[ER NUMBER]',
  phone: '[PHONE NUMBER]',
  email: '[EMAIL]',
  street: '[STREET ADDRESS]',
  cityState: '[CITY], [STATE]',
  parking: '[PARKING DETAILS]',
  // Visiting hours as 24h "HH:MM" strings (defaults from the admin Settings design).
  // Set start/end to null to show the [TIME] placeholder label and hide the "Open now" badge.
  // The admin can change all of these in Settings.
  visiting: { start: '10:00', end: '19:00', label: 'Mon–Sun, [TIME] to [TIME]', maxVisitors: 2 },
}

export const departments = [
  { name: 'Cardiology', info: 'Heart and blood vessel care.' },
  { name: 'Pediatrics', info: 'Care for babies, children and teens.' },
  { name: 'Maternity', info: 'Pregnancy, birth and newborn care.' },
  { name: 'Neurology', info: 'Brain, spine and nerve conditions.' },
  { name: 'Ophthalmology', info: 'Eye exams and eye surgery.' },
  { name: 'Orthopedics', info: 'Bones, joints and muscles.' },
  { name: 'Radiology', info: 'X-ray, CT, MRI and ultrasound.' },
  { name: 'Emergency', info: 'Urgent care, open 24/7.' },
]
