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
| Dentrove | Cloud (Kenya, Uganda, Tanzania) | Local competitor. Reminders by WhatsApp, SMS and email, recalls, invoicing in local currency, M-Pesa payments. |
| Deputy, Shifton, Shyfter | Staff rostering | Shifts, clock-in with PIN/photo, leave, some task assignment. Not connected to the patient schedule. |

**Gap we can fill:** most practice systems are built around reception and billing. Staff rostering tools are separate products. Very few give each dentist/nurse a simple "my day" view on their phone that updates live when reception changes something. That is our hook.

---

## 1. Data collected from patients

### Personal and contact
- Full name, preferred name
- National ID or passport number (birth certificate number for children)
- SHA (Social Health Authority) member number
- Date of birth, gender
- Address / estate, town
- Mobile phone (also used for M-Pesa), email
- Preferred contact method (SMS, WhatsApp, email, call). Many patients may not have a smartphone, so SMS must always work
- Preferred language (English, Kiswahili)
- Photo (optional, helps staff recognise the patient)
- How they heard about the clinic / referred by

### Emergency contact and responsible party
- Emergency contact name, relationship, phone
- Responsible party / guarantor (who pays, needed for minors)
- Parent or guardian details and consent for children

### Payer: cash, SHA, private insurance or employer scheme
- Payer type: cash / M-Pesa, SHA, private insurer, corporate scheme
- Private insurer (for example AAR, Jubilee, CIC, Britam, Madison) and scheme / employer name
- Principal member name and relationship to patient
- Member number, policy or scheme number
- Dental cover limit and amount used (dental limits in Kenya are often around KES 10,000 to 20,000 a year)
- Pre-authorisation needed? Pre-auth number and approval status
- Secondary cover (for example SHA plus a private insurer)

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
- Privacy notice acknowledgement (Kenya Data Protection Act 2019)
- Explicit consent to process health data (it is "sensitive personal data" under the Act)
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
- Payment method: M-Pesa (till / paybill, transaction code), cash, card, insurance
- Booking deposit paid by M-Pesa (local vendors report deposits of KES 500 to 1,000 sharply reduce no-shows)
- SHA and insurance claims, status, and date paid (insurers can take 3+ months to pay)

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
- KMPDC (Kenya Medical Practitioners and Dentists Council) registration number
- Annual practising licence: year and expiry. Renewal is due by 30 November each year, with penalties or suspension if late
- CPD points (at least 50 needed for licence renewal)
- Professional indemnity cover and expiry (required for renewal)
- Nursing Council of Kenya / other registration for nurses and other cadres
- CPR / BLS certification expiry
- The clinic's own KMPDC facility licence expiry

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

Kenya's rules come from the **Data Protection Act 2019** and the **Data Protection (General) Regulations 2021**, enforced by the **ODPC** (Office of the Data Protection Commissioner).

- **Health data is sensitive personal data.** Processing it is treated as high risk, so a Data Protection Impact Assessment (DPIA) is expected.
- **ODPC registration.** The clinic (data controller) must register with the ODPC; the certificate lasts 2 years. If we host the data for clinics, we are a **data processor** and must register too.
- **Breach notification.** The clinic must notify the ODPC within **72 hours**. As processor, we must tell the clinic within **48 hours**. Patients must be told if there is high risk to them.
- **Data localisation (Regulation 26).** Data processed for listed "strategic interests", which include primary and secondary healthcare, must be processed on a server in Kenya **or** have at least a copy stored in Kenya. Confirm the exact scope with a Kenyan data protection lawyer.
- **Cross-border transfer** of health data generally needs explicit consent plus an approved safeguard. The ODPC published new cross-border transfer guidance on 8 September 2026.
- The 2025 M-Tiba breach (health data of about 5 million Kenyans, detected 10 days late) shows why monitoring matters.

The app must have:
- Encrypted data in transit (HTTPS) and at rest
- Individual login per staff member (no shared accounts)
- Role based access control
- Audit trail
- Automatic logout on shared reception computers
- Daily backups
- Data hosted in Kenya, or at minimum a full copy kept in Kenya (see hosting below)
- Patient consent records
- A breach response plan that meets the 48 / 72 hour deadlines
- Monitoring and alerts for unusual access

---

## 6. Build list

### Phase 1: MVP (replace the paper book)
- [ ] Clinic setup: clinic details, opening hours, chairs/rooms, appointment types with default lengths
- [ ] Staff accounts with roles (owner, dentist, hygienist, nurse, reception) and login
- [ ] Patient records: personal, contact, emergency contact, medical alert, basic medical history, notes
- [ ] Fast patient search (name, phone, national ID)
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
- [ ] Works on slow mobile data, and basic offline mode: today's schedule stays visible and changes queue up during internet or power outages, then sync

### Phase 2: Patient facing and efficiency
- [ ] Appointment reminders and confirmations by SMS (always) and WhatsApp (patient replies to confirm)
- [ ] M-Pesa payments and booking deposits (Daraja API, STK push to the patient's phone)
- [ ] Digital new patient form on a tablet or link (replaces paper intake form, with e-signature consent)
- [ ] Online booking page
- [ ] ASAP / waitlist to fill cancellations
- [ ] No-show and unscheduled treatment lists
- [ ] Recall system (auto create recall due date after a cleaning, send reminder)
- [ ] Staff rostering: working hours, leave requests, chair assignment
- [ ] Credential tracking with KMPDC licence, CPD points, indemnity and CPR expiry alerts
- [ ] Morning huddle screen
- [ ] Reports: appointments per day, no-show rate, chair utilisation, waiting time
- [ ] Bilingual interface and messages (English / Kiswahili)

### Phase 3: Full practice management
- [ ] Tooth chart and perio chart
- [ ] Clinical notes with templates
- [ ] X-ray and photo upload
- [ ] Treatment plans with cost estimates
- [ ] Billing, invoices, receipts, payments
- [ ] SHA and private insurer claims, pre-authorisation tracking, cover limit balance, unpaid claims ageing
- [ ] Sterilisation log, opening/closing checklists, inventory, lab case tracking
- [ ] Multi-branch support
- [ ] Clock-in / clock-out
- [ ] Data import from other systems and export (so the clinic is never locked in)

### Suggested technology
- **Frontend:** a single web app (React / Next.js) that installs on phones as a PWA, so one codebase covers reception PC, tablets and phones
- **Backend:** Postgres with real-time sync, login and row level security. Supabase gives all of this and can be self-hosted
- **Payments:** Safaricom M-Pesa Daraja API
- **Messaging:** a Kenyan SMS gateway (for example Africa's Talking) plus WhatsApp Business API
- **Hosting:** none of AWS, Azure, Google or Supabase cloud has a region in Kenya (the closest are in South Africa; Oracle has announced a Nairobi region). Because of Regulation 26, the safest choice is to **run the database on a server in a Nairobi data centre** (for example iXAfrica, Africa Data Centres or Safaricom), self-hosting Supabase there. The alternative is a South Africa cloud region **plus** a continuously updated copy in Kenya, which needs legal sign-off

---

## 7. Questions to ask the dentist before building

1. How many dentists, nurses, hygienists and receptionists? How many chairs?
2. How do they book now: phone, walk-in, WhatsApp?
3. What do they write in the book for each appointment?
4. What tasks do nurses check with reception for, besides patient list?
5. Do they already use any software for billing or X-rays?
6. What share of patients pay cash / M-Pesa vs SHA vs private insurance? Which insurers?
7. Do they have an M-Pesa till or paybill number?
8. Do they want patients to book online? Would they charge a booking deposit?
9. What devices do the staff have: own phones, clinic tablets, one reception PC?
10. How reliable are internet and power at the clinic? Is there a backup?
11. Are they registered with the ODPC as a data controller?
12. Who would pay, and how much would they pay per month? (Local tools charge roughly KES 3,000 to 9,000 a month)

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
- [ODPC: Data Protection (General) Regulations 2021](https://www.odpc.go.ke/wp-content/uploads/2024/03/THE-DATA-PROTECTION-GENERAL-REGULATIONS-2021-1.pdf)
- [ODPC: Data Controllers compliance card](https://www.odpc.go.ke/wp-content/uploads/2025/07/Data-Controllers-360-Degrees-Compliance-Requirements.-Z-Card.pdf)
- [ODPC: Report a Data Breach](https://www.odpc.go.ke/report-a-data-breach/)
- [Digital Policy Alert: Kenya data localisation requirement](https://digitalpolicyalert.org/change/13685-data-localisation-requirement-in-data-protection-general-regulations)
- [Oraro: Data Protection Regulations coming into force](https://www.oraro.co.ke/data-protection-the-coming-into-force-of-various-data-protection-regulations-and-what-you-need-to-know/)
- [Inside Privacy: Kenya cross-border transfer guidance](https://www.insideprivacy.com/cross-border-transfers/kenya-issues-new-cross-border-data-transfer-guidance-familiar-concepts-but-important-local-differences/)
- [Bowmans: Data breaches under Kenyan law](https://bowmanslaw.com/insights/kenya-a-few-insights-on-navigating-data-breaches-in-kenya-under-the-kenyan-data-protection-law/)
- [TechCabal: M-Tiba breach](https://techcabal.com/2025/11/12/m-tiba-took-10-days-detect-breach-exposed-kenyan-health-data/)
- [KMPDC: Renewal notice for health institutions 2026](https://kmpdc.go.ke/resources/notices/2025/RENEWAL_NOTICE_TO_ALL_HEALTH_INSTITUTIONS_FOR_THE_YEAR_2026.pdf)
- [Kenya Times: KMPDC licence renewal](https://thekenyatimes.com/health/kmpdc-license-renewal/)
- [Strathmore: Kenyan dentists' experience with dental insurance](https://su-plus.strathmore.edu/handle/11071/4589)
- [Huduma Global: NHIF, SHA and private cover](https://hudumaglobal.com/blog/understanding-health-insurance-kenya-nhif-sha-private-medical-cover)
- [AllAfrica: SHA registrations and NHIF debts](https://allafrica.com/stories/202606110484.html)
- [Dentrove](https://slashdot.org/software/p/Dentrove/)
- [Kolonell: Dental clinic app Nairobi 2026](https://kolonell.com/en/blog/dental-clinic-management-app-appointments-reminders-nairobi-2026)
- [Supabase: Available regions](https://supabase.com/docs/guides/platform/regions)
- [Developing Telecoms: Oracle cloud in Kenya](https://developingtelecoms.com/telecom-technology/telecom-cloud-virtualization/16151-oracle-expands-african-cloud-presence-into-kenya.html)
