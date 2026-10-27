# Dental Clinic Scheduler: Research and Build List

## The problem we saw

At the dentist, reception keeps appointments, schedules and patient details in a paper book. Dentists and nurses walk to reception to find out what patients and tasks they have. The goal: one system where reception (on a computer) and clinical staff (on phone or tablet) see the same live schedule, synced instantly.

## What already exists

| Product | Type | Notes |
|---|---|---|
| Dentrix / Dentrix Ascend | Desktop / cloud | Market leader in the US. ASAP list, unscheduled list, broken/no-show pinboard, 200+ permission settings, audit trail. |
| Open Dental | Desktop (self-hosted) | Flat per-location price, very customizable. Detailed appointment model (provider, hygienist, operatory, confirmation, arrived/seated/dismissed times). |
| Curve Dental | Cloud (browser) | Top user ratings for appointment management. Charting plus patient engagement in one. |
| Denticon | Cloud | Aimed at large groups (DSOs). Heavy for a single clinic. |
| kumoDent, CxSYS | Malaysian systems | kumoDent is cloud with real-time EMR sync across branches. CxSYS runs on the clinic network or cloud. |
| Deputy, Shifton, Shyfter | Staff rostering | Shifts, clock-in with PIN/photo, leave, some task assignment. Not connected to the patient schedule. |

**Gap we can fill:** most practice systems are built around reception and billing. Staff rostering tools are separate products. Very few give each dentist/nurse a simple "my day" view on their phone that updates live when reception changes something. That is our hook.

---

## 1. Data collected from patients

### Personal and contact
- Full name, preferred name
- IC / passport number (or national ID for the country)
- Date of birth, gender
- Address
- Mobile phone, email
- Preferred contact method (SMS, WhatsApp, email, call)
- Preferred language
- Photo (optional, helps staff recognise the patient)
- How they heard about the clinic / referred by

### Emergency contact and responsible party
- Emergency contact name, relationship, phone
- Responsible party / guarantor (who pays, needed for minors)
- Parent or guardian details and consent for children

### Insurance / panel / employer coverage
- Primary carrier (insurer, panel or company scheme)
- Subscriber name and date of birth, relationship to patient
- Member ID, policy or group number, insurer phone
- Secondary coverage (some patients have two)
- Coverage limits and remaining balance (if the clinic tracks it)

### Medical history
- Under a doctor's care? Doctor name and phone
- Current medications
- Allergies (drugs such as penicillin, latex, anaesthetic)
- Conditions: heart disease, high blood pressure, diabetes, bleeding disorders, asthma, epilepsy, hepatitis, HIV, etc.
- Pregnancy / breastfeeding
- Recent hospitalisations or surgeries
- Smoking / alcohol
- **Medical alert flag** shown on every appointment (for example "Allergic to penicillin")

### Dental history
- Reason for visit / chief complaint
- Last dental visit, last X-ray
- Previous dentist
- Concerns: sensitivity, bleeding gums, grinding, jaw pain, anxiety
- Past treatments (braces, implants, root canals)

### Consent and privacy
- Privacy notice acknowledgement (PDPA in Malaysia, HIPAA in the US, GDPR in Europe)
- Consent to treatment
- Consent to receive reminders by SMS/WhatsApp/email
- Authorisation to share info with insurer or other providers
- Digital signature and date

### Clinical record (phase 2+)
- Tooth chart (odontogram): existing work, missing teeth, conditions per tooth
- Periodontal chart: probing depths, bleeding on probing, recession, mobility, furcation
- Clinical notes per visit
- X-rays and intraoral photos
- Treatment plan: procedures, tooth, cost estimate, accepted or declined
- Completed procedures with procedure codes

### Financial
- Invoices, payments, balance owing
- Payment method
- Insurance claims and status

### Recall
- Recall type (cleaning every 6 months, check-up, perio maintenance every 3 months)
- Next due date
- Reminder history

---

## 2. Data on each appointment

Modelled on Open Dental and Dentrix:

- Patient
- **Dentist** (required)
- **Hygienist** (optional)
- **Assistant / nurse** (optional)
- **Chair / room** (called "operatory" in most systems)
- Date, start time, length (in 10 or 15 minute blocks)
- Appointment type: new patient exam, cleaning, filling, extraction, root canal, emergency, follow-up, consultation
- Planned procedures (and teeth involved)
- Notes for staff (for example "Nervous patient, needs extra time")
- Medical alert (pulled from patient record)
- Lab case attached (crown, denture) and whether it has come back
- Booked by (staff member) and source (walk-in, phone, online)
- ASAP flag (patient wants an earlier slot if one opens up)

### Status flow
`Scheduled → Confirmed → Arrived → Seated (in chair) → Completed`

Plus: `Cancelled`, `No-show / Broken`, `Rescheduled`, `Unscheduled` (needs a new date).

### Timestamps
- Time arrived, time seated, time dismissed (gives waiting time and chair time reports)

### Lists the receptionist works from
- ASAP list (fill cancellations)
- Broken / no-show list (call back)
- Unscheduled treatment (planned but never booked)
- Recall due list

---

## 3. Data on staff (dentists, hygienists, nurses, reception)

### Profile
- Name, photo, phone, email
- Role: dentist, hygienist, dental nurse/assistant, receptionist, clinic manager, owner
- Specialty (orthodontics, endodontics, etc.)
- Colour on the schedule

### Credentials
- Registration / licence number (for example Malaysian Dental Council APC)
- Licence expiry date with reminders
- CPR / BLS certification expiry
- Continuing education records

### Availability
- Working days and hours per week
- Which chair/room they usually work in
- Leave and time off requests, public holidays
- Shift swaps
- Clock-in / clock-out (optional, with PIN or photo)

### Tasks and assignments (the main pain point)
- Appointments assigned to them today
- Which nurse is paired with which dentist
- Non-patient tasks: sterilisation, opening/closing checklist, restock, lab follow-up, call back patients
- Task status: to do, in progress, done, with who completed it and when

### Access
- Login, role based permissions (a nurse should not see financials, reception may not edit clinical notes)
- Audit log of who viewed or changed what

---

## 4. Daily clinic operations

- **Morning huddle sheet:** today's patients, medical alerts, unpaid balances, lab cases due, gaps to fill
- **Sterilisation log:** each autoclave cycle, load contents, chemical and biological indicator results, operator
- **Opening and closing checklists**
- **Lab case tracking:** sent date, lab name, due date, received
- **Inventory:** low stock alerts (gloves, anaesthetic, impression material)

---

## 5. Privacy and security requirements

Health data is "sensitive personal data" under Malaysia's PDPA. The 2024 amendments (in force through 2025) add mandatory breach notification to the Commissioner and raise fines to RM1,000,000 and/or 3 years jail. The US equivalent is HIPAA; Europe is GDPR.

The app must have:
- Encrypted data in transit (HTTPS) and at rest
- Individual login per staff member (no shared accounts)
- Role based access control
- Audit trail
- Automatic logout on shared reception computers
- Daily backups
- Data hosted in a region acceptable to the clinic (check cross-border transfer rules)
- Patient consent records
- A breach response plan

---

## 6. Build list

### Phase 1: MVP (replace the paper book)
- [ ] Clinic setup: clinic details, opening hours, chairs/rooms, appointment types with default lengths
- [ ] Staff accounts with roles (owner, dentist, hygienist, nurse, reception) and login
- [ ] Patient records: personal, contact, emergency contact, medical alert, basic medical history, notes
- [ ] Fast patient search (name, phone, IC)
- [ ] Appointment book: day/week view, columns per chair or per dentist, drag to move, colour by dentist/type
- [ ] Create / edit / cancel / reschedule appointments
- [ ] Assign dentist + nurse + chair to each appointment, with double booking prevention
- [ ] Status flow: scheduled, confirmed, arrived, seated, completed, no-show, cancelled
- [ ] **"My Day" mobile view** for each dentist and nurse: their patients, times, chair, medical alerts, notes
- [ ] **Real-time sync** between reception computer and staff phones
- [ ] Push notification to the dentist/nurse when their patient arrives or a booking changes
- [ ] Simple task list: reception or manager assigns tasks to staff, staff tick them off
- [ ] Audit log and role based permissions
- [ ] Works on desktop browser and phone (installable web app)

### Phase 2: Patient facing and efficiency
- [ ] Appointment reminders and confirmations by SMS / WhatsApp / email (patient replies to confirm)
- [ ] Digital new patient form on a tablet or link (replaces paper intake form, with e-signature consent)
- [ ] Online booking page
- [ ] ASAP / waitlist to fill cancellations
- [ ] No-show and unscheduled treatment lists
- [ ] Recall system (auto create recall due date after a cleaning, send reminder)
- [ ] Staff rostering: working hours, leave requests, chair assignment
- [ ] Credential tracking with licence / CPR expiry alerts
- [ ] Morning huddle screen
- [ ] Reports: appointments per day, no-show rate, chair utilisation, waiting time
- [ ] Offline mode (keep working if the internet drops, sync when back)

### Phase 3: Full practice management
- [ ] Tooth chart and perio chart
- [ ] Clinical notes with templates
- [ ] X-ray and photo upload
- [ ] Treatment plans with cost estimates
- [ ] Billing, invoices, receipts, payments
- [ ] Insurance / panel claims
- [ ] Sterilisation log, opening/closing checklists, inventory, lab case tracking
- [ ] Multi-branch support
- [ ] Clock-in / clock-out
- [ ] Data import from other systems and export (so the clinic is never locked in)

### Suggested technology
- **Frontend:** a single web app (React / Next.js) that installs on phones as a PWA, so one codebase covers reception PC, tablets and phones
- **Backend:** Supabase (Postgres database, login, real-time sync, row level security for permissions, file storage for X-rays) or Firebase
- **Messaging:** WhatsApp Business API / Twilio for reminders
- **Hosting:** region close to the clinic (for Malaysia, Singapore region) to meet PDPA expectations

---

## 7. Questions to ask the dentist before building

1. How many dentists, nurses, hygienists and receptionists? How many chairs?
2. How do they book now: phone, walk-in, WhatsApp?
3. What do they write in the book for each appointment?
4. What tasks do nurses check with reception for, besides patient list?
5. Do they already use any software for billing or X-rays?
6. Do they deal with insurance or company panels?
7. Do they want patients to book online?
8. What devices do the staff have: own phones, clinic tablets, one reception PC?
9. Is internet at the clinic reliable?
10. Who would pay, and how much would they pay per month?

---

## Sources

- [Curve Dental: Best Dental Practice Management Software 2026](https://www.curvedental.com/dental-blog/best-dental-practice-management-software-2026)
- [Layer3 Labs: Best Dental Practice Management Software](https://www.layer3labs.io/comparisons/best-dental-practice-management-software)
- [SelectHub: Dentrix vs Curve Dental](https://www.selecthub.com/dental-software/dentrix-vs-curve-dental/)
- [Capterra: Curve Dental vs Dentrix Enterprise](https://www.capterra.com/compare/98688-161566/Curve-Dental-Hero-vs-Dentrix-Enterprise)
- [Open Dental Manual: Edit Appointment](https://opendental.com/manual/aptedit.html)
- [Open Dental Manual: Operatory Edit](https://opendental.com/manual/operatoryedit.html)
- [Dental Intelligence: Confirming appointments in Open Dental](https://learn.dentalintel.com/en/articles/5528561-open-dental-confirming-appointments-in-dental-intelligence)
- [Dentrix Enterprise: I need an appointment ASAP](https://blog.dentrixenterprise.com/i-need-an-appointment-asap/)
- [Dentrix Ascend: ASAP list](https://blog.dentrixascend.com/tag/asap-list)
- [Dentrix Ascend: ASAP option for online bookings](https://learn.dentrixascend.com/asap-option-for-appointments-booked-online/)
- [Dentrix Enterprise: Multi-location features](https://dentrixenterprise.com/products/multi-location/features)
- [Jotform: New Patient Registration Form](https://form.jotform.com/220032252389449)
- [Poper: Dental Patient Intake Form](https://www.poper.ai/templates/forms/dental-patient-intake-form/)
- [Paperform: Dental Practice New Patient Intake Form](https://paperform.co/templates/dental-practice-new-patient-intake-form/)
- [Pabau: Dental New Patient Form](https://pabau.com/templates/dental-new-patient-form/)
- [Denti.AI: Perio Charting Guide](https://www.denti.ai/blog-posts/perio-charting-guide)
- [PMC: Dental EHR periodontal chart study](https://pmc.ncbi.nlm.nih.gov/articles/PMC4081750)
- [Curve Dental: Dental Clinic Software Guide](https://www.curvedental.com/dental-blog/dental-clinic-software-a-complete-guide-to-modern-practice-management)
- [Curve Dental: Dental Assistant Checklist](https://www.curvedental.com/dental-blog/dental-assistant-checklist)
- [Paperform: Sterilisation and Infection Control Checklist](https://paperform.co/templates/dental-office-sterilization-and-infection-control-daily-checklist)
- [Paperform: Dental Office Daily Operations Report](https://paperform.co/templates/dental-office-daily-operations-report/)
- [Everhour: Dental Scheduling Software](https://everhour.com/shifts/dental-scheduling-software)
- [OnTheClock: Time Clock Software for Dental Offices](https://www.ontheclock.com/blog/best-time-clock-software-for-dental-offices)
- [Shyfter: Dental Practice Staff Scheduling](https://shyfter.co/en/industries/healthcare/dental-practice-staff-scheduling)
- [HR Cloud: Dental HR Management](https://www.hrcloud.com/dental-hr-management-software)
- [CE Zoom: Office Compliance](https://cezoom.com/office-compliance)
- [NST: Cloud-based kumoDent](https://www.nst.com.my/amp/business/home/2021/11/747654/cloud-based-kumodent-aims-secure-300-dental-clinics-use-its-system-2022)
- [DLA Piper: Data Protection in Malaysia](https://www.dlapiperdataprotection.com/?c=MY&t=law)
- [Sidley: Important changes to Malaysia's data protection laws](https://www.sidley.com/en/insights/newsupdates/2024/08/important-changes-to-malaysias-data-protection-laws)
- [Rajah & Tann: PDPA Amendment Bill 2024 analysis](https://www.rajahtannasia.com/?p=154128)
