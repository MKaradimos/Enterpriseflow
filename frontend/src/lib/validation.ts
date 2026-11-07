// Validation schemas shared by several forms.

import { z } from 'zod'

/** Same policy as the backend (UserRequests.PASSWORD_REGEX): 10-72 chars, at least one letter and one digit. */
export const passwordSchema = z
  .string()
  .min(10, 'At least 10 characters')
  .max(72, 'At most 72 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one digit')
