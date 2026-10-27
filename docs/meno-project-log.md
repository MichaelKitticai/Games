# Meno: Project Log

A record of the conversation that started **Meno**, a dental clinic scheduling app for Nairobi, Kenya. It covers the idea, the research, decisions made along the way, the homepage design options, and the app scaffold with over-the-air (OTA) updates.

- **Branch:** `claude/dental-scheduler-research-775l4l` in `michaelkitticai/games`
- **Design canvas:** https://claude.ai/artifact/7msg4uEN6qRpcLaYrV4xuJ (private until shared from its Share menu)
- **Full research document:** [`research/dental-scheduler-research.md`](../research/dental-scheduler-research.md)
- **App code:** [`meno/`](../meno/)

---

## 1. The idea

At a dental visit, the receptionist was recording all appointments, schedules and patient data in a paper book. Dentists and nurses had to walk to reception to find out which patients and tasks they had.

**Goal:** an app where reception enters data on a computer and everything syncs instantly to the dentists' and nurses' phones, so nobody has to check the book at the front desk.

---

## 2. How the conversation went

| Step | Request | Outcome |
|---|---|---|
| 1 | Research dental scheduling software and the data they collect from patients and staff, then list what to build | Research document with data lists, a 3-phase build list and questions for the dentist |
| 2 | "Why Singapore when we're building for Kenya?" | The first version wrongly assumed Malaysia (and suggested Singapore hosting). Redone for Kenya: Data Protection Act 2019, ODPC, SHA, M-Pesa, KMPDC |
| 3 | "Specifically Nairobi" | Added Nairobi section: traffic, power cuts, estate addressing, county business permit, Nairobi data centres |
| 4 | Keep data, location and laws Kenyan; UI and everything else international | Added the "international product, Kenyan rules" split, FDI tooth numbering, HL7 FHIR, Digital Health Act 2023 |
| 5 | Kenya only. Start building: 3 homepage designs on a canvas, and OTA updates in the repo | Canvas with options A, B and C (desktop + phone each). Expo app scaffold with EAS Update and GitHub workflows |
| 6 | This document | `docs/meno-project-log.md` |

---

## 3. Research summary

### Existing products

| Product | Notes |
|---|---|
| Dentrix / Dentrix Ascend | US market leader. ASAP list, unscheduled list, broken/no-show list, 200+ permission settings, audit trail |
| Open Dental | Flat per-location price, very customizable. Appointment has dentist (required), hygienist (optional), operatory (chair), confirmation status, arrived / seated / dismissed times |
| Curve Dental | Cloud, top user ratings for appointment management |
| Denticon | Cloud, for large dental groups |
| Dentrove | Local competitor (Kenya, Uganda, Tanzania): WhatsApp/SMS/email reminders, recalls, M-Pesa payments |
| Deputy, Shifton, Shyfter | Staff rostering and clock-in, not linked to the patient schedule |

**Our gap:** most systems centre on reception and billing; staff tools are separate. Few give each dentist and nurse a live "my day" view on their phone. That is Meno's hook.

### Data collected from patients
- **Personal:** name, national ID or passport (birth certificate for children), SHA number, date of birth, gender, address (estate / area, road, building, landmark), mobile (also M-Pesa), email, preferred contact (SMS always, WhatsApp, email, call), language (English, Kiswahili), photo, referral source
- **Emergency contact and responsible party / guardian**
- **Payer:** cash / M-Pesa, SHA, private insurer (AAR, Jubilee, CIC, Britam, Madison), corporate scheme; principal member name, date of birth and relationship; member / policy number; insurer phone; dental cover limit and amount used (often KES 10,000 to 20,000 a year); pre-authorisation status; secondary cover
- **Medical history:** doctor, medications, allergies, conditions (heart, blood pressure, diabetes, bleeding disorders, asthma, epilepsy, hepatitis, HIV), pregnancy, hospitalisations, smoking / alcohol. A **medical alert flag** shows on every appointment
- **Dental history:** chief complaint, last visit and X-ray, previous dentist, concerns, past treatment
- **Consent:** privacy notice (Data Protection Act 2019), explicit consent for health data, consent to treatment and to reminders, sharing with insurers, e-signature
- **Clinical (later):** tooth chart (FDI numbering), perio chart, notes, X-rays and photos, treatment plans, completed procedures
- **Financial:** invoices, M-Pesa transaction codes, booking deposits, SHA / insurance claims and payment dates
- **Recall:** type, due date, reminder history

### Data on each appointment
Patient, dentist, hygienist, nurse, chair, date, start, length, type, planned procedures and teeth, staff notes, medical alert, lab case, booked by, source, ASAP flag.

**Status flow:** Scheduled > Confirmed > Arrived > Seated > Completed, plus Cancelled, No-show, Rescheduled, Unscheduled. Timestamps for arrived, seated and dismissed.

**Reception work lists:** ASAP list, no-show list, unscheduled treatment, recall due.

### Data on staff
Profile, role, specialty, schedule colour; KMPDC registration number, annual practising licence (renew by 30 November), CPD points (50 needed), professional indemnity, Nursing Council registration, CPR / BLS expiry; working hours, usual chair, leave, shift swaps, clock-in; assigned appointments and tasks; role-based permissions and audit log.

### Daily operations
Morning huddle, sterilisation (autoclave) log, opening / closing checklists, lab case tracking, inventory.

---

## 4. Key decisions

1. **Kenya only, Nairobi first.** No Malaysia or other countries.
2. **International product, Kenyan rules.**

| International (like Dentrix, Curve, Open Dental) | Kenyan / Nairobi |
|---|---|
| UI and workflows: chair/provider book, drag and drop, status flow, ASAP / no-show / recall lists, huddle | Data Protection Act 2019, ODPC |
| Full international patient record | Digital Health Act 2023 and 2025 regulations |
| FDI / ISO 3950 tooth numbering, standard perio chart, ICD diagnosis codes | Hosting in Nairobi |
| HL7 FHIR APIs, data export | M-Pesa, KES |
| HIPAA / GDPR / ISO 27001 style security | SHA, Kenyan insurers and schemes |
| WCAG accessibility, mobile first | KMPDC, Nursing Council, Nairobi County UBP, KNRA, ODPC |
| Staff roles, credentials, rosters, tasks | National ID, +254 numbers, estate addresses, English / Kiswahili |

3. **Hosting in Nairobi.** AWS, Azure, Google and Supabase cloud have no Kenya region. Plan: rent a server in a Nairobi data centre (iXAfrica NBOX1 or Africa Data Centres NBO1, via a Kenyan host) and self-host Supabase (Postgres, login, real-time sync, row level security). This also satisfies Regulation 26 (healthcare data processed in Kenya or a copy kept in Kenya).
4. **Tech stack:** Expo (React Native) so one codebase serves Android, iOS and the reception PC browser. EAS Update for OTA updates. M-Pesa Daraja API, a Kenyan SMS gateway (for example Africa's Talking) and WhatsApp Business API.
5. **Working name:** "Meno" (Kiswahili for "teeth"). Placeholder, can change.

---

## 5. Kenyan legal and compliance checklist

- **Data Protection Act 2019 + General Regulations 2021 (ODPC)**
  - Health data is sensitive personal data; explicit consent; Data Protection Impact Assessment expected
  - Clinic registers with ODPC as data controller (certificate valid 2 years); Meno registers as data processor
  - Breach: processor tells clinic within 48 hours; clinic tells ODPC within 72 hours; tell patients if high risk
  - Regulation 26: healthcare data processed on a server in Kenya, or a copy stored in Kenya (confirm scope with a Kenyan data protection lawyer)
  - Cross-border transfer needs explicit consent and safeguards (ODPC guidance, 8 Sept 2026)
- **Digital Health Act 2023 + April 2025 regulations:** certification of digital health systems by the Digital Health Agency; national health data exchange rules. Confirm criteria with the Agency before launch.
- **Clinic licences to track:** Nairobi County Unified Business Permit (Jan to Dec, up to 3% a month late penalty, via NairobiPay), KMPDC facility and practitioner licences, public health inspection, fire certificate, KNRA X-ray licence, ODPC registration.

**Security must-haves:** encryption in transit and at rest, individual logins, role-based access, audit trail, auto logout on shared PCs, daily backups, data in Nairobi, consent records, breach response plan, access monitoring.

---

## 6. Build list

### Phase 1: MVP (replace the paper book)
- [ ] Clinic setup: hours, chairs, appointment types and lengths
- [ ] Staff accounts with roles and login
- [ ] Patient records with medical alert and history
- [ ] Fast search by name, phone, national ID
- [ ] Appointment book: day/week, columns per chair or dentist, drag to move, colour coding
- [ ] Create / edit / cancel / reschedule
- [ ] Assign dentist + nurse + chair, prevent double booking
- [ ] Status flow
- [ ] "My Day" phone view for dentists and nurses
- [ ] Real-time sync between reception and phones
- [ ] Push notification when a patient arrives or a booking changes
- [ ] Task list assigned by reception / manager
- [ ] Audit log and permissions
- [ ] Desktop browser + installable phone app
- [ ] Works on slow mobile data; basic offline mode during power or internet cuts
- [ ] "Patient running late" status

### Phase 2: Patient facing and efficiency
- [ ] Clinic compliance tracker with expiry reminders
- [ ] SMS (always) and WhatsApp reminders and confirmations
- [ ] M-Pesa payments and booking deposits (STK push)
- [ ] Digital new patient form with e-signature consent
- [ ] Online booking
- [ ] ASAP / waitlist
- [ ] No-show and unscheduled treatment lists
- [ ] Recall system
- [ ] Staff rostering and leave
- [ ] Credential tracking (KMPDC, CPD, indemnity, CPR)
- [ ] Morning huddle screen
- [ ] Reports: appointments, no-show rate, chair use, waiting time
- [ ] English / Kiswahili

### Phase 3: Full practice management
- [ ] Tooth chart (FDI default) and perio chart
- [ ] Clinical notes with templates
- [ ] X-ray and photo upload
- [ ] Treatment plans with estimates
- [ ] Billing, invoices, receipts
- [ ] SHA and insurer claims, pre-auth tracking, unpaid claims ageing
- [ ] Sterilisation log, checklists, inventory, lab cases
- [ ] Multi-branch
- [ ] Clock-in / clock-out
- [ ] Data import / export
- [ ] HL7 FHIR API
- [ ] Digital Health Agency certification

---

## 7. Homepage design options

Canvas: https://claude.ai/artifact/7msg4uEN6qRpcLaYrV4xuJ. Each option has a reception desktop screen and a matching phone screen. All names are sample data.

### Option A: Chair Board
- **Look:** Inter font, white and light grey, teal accent
- **Desktop:** time grid (8:00 to 13:00) with a column per chair; appointments coloured by dentist with status pills and medical alerts; red "now" line; side panel with waiting room and "Needs attention" (no-show to rebook, unconfirmed booking, lab case due, KMPDC renewals)
- **Phone:** "My day, Dr. Mwangi": arrival notification, current patient card (allergy, Notes, Mark done), next patients, bottom tabs
- **Best for:** clinics used to a traditional appointment book

### Option B: Patient Flow
- **Look:** Manrope font, navy sidebar, amber accent
- **Desktop:** columns Booked > Arrived > In chair > To pay > Done; cards with payer (SHA, AAR, M-Pesa), alerts, wait timers, actions ("Send to chair", "Send M-Pesa request"); sync status for all devices
- **Phone:** "4 patients left today", in-chair patient card with progress (Called in, Treating, Finished) and "Finish and send to pay", up next, my tasks
- **Best for:** busy clinics focused on moving patients through quickly

### Option C: Today at a Glance
- **Look:** Plus Jakarta Sans, soft green-grey, green accent, top navigation
- **Desktop:** "Habari, Wanjiku"; tiles (appointments, waiting, in chair, no-shows); up next with actions; team today with status; tasks; payments today (M-Pesa); coming up (lab, KMPDC, recalls)
- **Phone:** nurse view "Habari, Mary": current patient, what to prepare for upcoming patients, assigned tasks
- **Best for:** owners and managers wanting an overview, calmer feel

**Pending decision:** choose A, B, C, or a mix (for example B desktop with A phone).

---

## 8. The app scaffold (`meno/`)

- **Stack:** Expo SDK 58, React Native 0.88, React 19, Expo Router, TypeScript
- **Platforms:** Android, iOS, web (reception PC)
- **Files:**
  - `src/app/_layout.tsx`: root layout with the update banner
  - `src/app/index.tsx`: placeholder home showing version, channel and update ID
  - `src/components/UpdateBanner.tsx`: checks for updates on launch and when the app returns to the foreground, downloads in the background, shows "Restart"
  - `app.json`: name Meno, scheme `meno`, bundle ID / package `ke.meno.app`, `runtimeVersion` policy `appVersion`, updates on load
  - `eas.json`: build profiles and channels (development, preview, production)
  - `README.md`: how to run and how OTA works
- **GitHub workflows:**
  - `.github/workflows/meno-ci.yml`: typecheck and web bundle on pull requests and non-main pushes
  - `.github/workflows/meno-ota.yml`: on push to `main` (changes in `meno/`), publishes an EAS Update to `production`; run manually to choose `preview`
- **Verified:** typecheck passes; web build bundles and renders with no errors

### How OTA updates work
1. Push code to `main`.
2. GitHub Actions publishes the update to the production channel.
3. Installed apps check on launch and on return to foreground, download it, and show "Restart".
4. Changes to native modules or native config need a new build: bump `version` in `app.json`, then `npx eas-cli build`.
5. The web version updates by redeploying the `dist/` folder from `npm run export:web`.

### One-time setup (needs a free Expo account)
```bash
cd meno
npm install
npx eas-cli login
npx eas-cli init               # adds extra.eas.projectId to app.json
npx eas-cli update:configure   # adds updates.url to app.json
npx eas-cli build --profile preview --platform android
```
Commit the `app.json` changes, then add an Expo access token as the `EXPO_TOKEN` secret in GitHub (Settings > Secrets and variables > Actions).

### Run locally
```bash
git clone https://github.com/michaelkitticai/games.git
cd games
git checkout claude/dental-scheduler-research-775l4l
cd meno
npm install
npm start      # Expo Go QR code, or press w for web
```

---

## 9. Questions for the dentist

1. How many dentists, nurses, hygienists, receptionists? How many chairs?
2. How do patients book now: phone, walk-in, WhatsApp?
3. What is written in the book for each appointment?
4. What do nurses check with reception for, besides the patient list?
5. Any existing software for billing or X-rays?
6. Share of patients paying cash / M-Pesa vs SHA vs private insurance? Which insurers?
7. M-Pesa till or paybill number?
8. Online booking wanted? Booking deposit?
9. Staff devices: own phones, clinic tablets, one reception PC?
10. Internet and power reliability? Backup?
11. Registered with the ODPC as a data controller?
12. Budget per month? (Local tools charge roughly KES 3,000 to 9,000)
13. Which part of Nairobi, where do patients come from, how often are they late?
14. Which permits and licences are renewed yearly, and who tracks them now?

---

## 10. Next steps

1. Pick a homepage design (A, B, C or a mix).
2. Do the one-time Expo setup and add `EXPO_TOKEN` to GitHub.
3. Visit the dentist with the questions above.
4. Build the chosen homepage in the app with sample data.
5. Set up the backend (self-hosted Supabase in a Nairobi data centre): data model for patients, appointments, staff, tasks.
6. Login and roles, then real-time sync and the "My Day" phone view.
7. Speak to a Kenyan data protection lawyer (Regulation 26, ODPC registration) and the Digital Health Agency (certification).

---

## 11. Sources

**Dental software and data**
- [Curve Dental: Best Dental Practice Management Software 2026](https://www.curvedental.com/dental-blog/best-dental-practice-management-software-2026)
- [Layer3 Labs: Best Dental Practice Management Software](https://www.layer3labs.io/comparisons/best-dental-practice-management-software)
- [SelectHub: Dentrix vs Curve Dental](https://www.selecthub.com/dental-software/dentrix-vs-curve-dental/)
- [Capterra: Curve Dental vs Dentrix Enterprise](https://www.capterra.com/compare/98688-161566/Curve-Dental-Hero-vs-Dentrix-Enterprise)
- [Open Dental: Edit Appointment](https://opendental.com/manual/aptedit.html)
- [Open Dental: Operatory Edit](https://opendental.com/manual/operatoryedit.html)
- [Dentrix Enterprise: ASAP](https://blog.dentrixenterprise.com/i-need-an-appointment-asap/)
- [Dentrix Ascend: ASAP list](https://blog.dentrixascend.com/tag/asap-list)
- [Dentrix Enterprise: features](https://dentrixenterprise.com/products/multi-location/features)
- [Jotform: New Patient Registration](https://form.jotform.com/220032252389449)
- [Paperform: New Patient Intake](https://paperform.co/templates/dental-practice-new-patient-intake-form/)
- [Denti.AI: Perio charting](https://www.denti.ai/blog-posts/perio-charting-guide)
- [Curve Dental: Dental assistant checklist](https://www.curvedental.com/dental-blog/dental-assistant-checklist)
- [Paperform: Sterilisation checklist](https://paperform.co/templates/dental-office-sterilization-and-infection-control-daily-checklist)
- [Shyfter: Dental staff scheduling](https://shyfter.co/en/industries/healthcare/dental-practice-staff-scheduling)
- [Wikipedia: Dental notation](https://en.wikipedia.org/wiki/Dental_notation)

**Kenya law and regulation**
- [ODPC: General Regulations 2021](https://www.odpc.go.ke/wp-content/uploads/2024/03/THE-DATA-PROTECTION-GENERAL-REGULATIONS-2021-1.pdf)
- [ODPC: Data Controllers compliance card](https://www.odpc.go.ke/wp-content/uploads/2025/07/Data-Controllers-360-Degrees-Compliance-Requirements.-Z-Card.pdf)
- [ODPC: Report a Data Breach](https://www.odpc.go.ke/report-a-data-breach/)
- [Digital Policy Alert: Kenya data localisation](https://digitalpolicyalert.org/change/13685-data-localisation-requirement-in-data-protection-general-regulations)
- [Inside Privacy: Kenya cross-border guidance](https://www.insideprivacy.com/cross-border-transfers/kenya-issues-new-cross-border-data-transfer-guidance-familiar-concepts-but-important-local-differences/)
- [Digital Health (Health Information Management Procedures) Regulations 2025](https://libraryir.parliament.go.ke/bitstreams/6aa4f05c-662b-4ea2-96fc-a8d2ae40f4df/download)
- [Clyde & Co: Health data protection in Kenya](https://www.clydeco.com/en/insights/2026/03/health-data-protection-in-kenya-strategic-complian)
- [KMPDC: Renewal notice 2026](https://kmpdc.go.ke/resources/notices/2025/RENEWAL_NOTICE_TO_ALL_HEALTH_INSTITUTIONS_FOR_THE_YEAR_2026.pdf)
- [Kenya Times: KMPDC licence renewal](https://thekenyatimes.com/health/kmpdc-license-renewal/)
- [Nairobi County: Unified Business Permit](https://nairobi.go.ke/unified-business-permit-ubp-usage)

**Kenya market and infrastructure**
- [Strathmore: Kenyan dentists and insurance](https://su-plus.strathmore.edu/handle/11071/4589)
- [AllAfrica: SHA registrations](https://allafrica.com/stories/202606110484.html)
- [Dentrove](https://slashdot.org/software/p/Dentrove/)
- [Kolonell: Nairobi clinic apps](https://kolonell.com/en/blog/dental-clinic-management-app-appointments-reminders-nairobi-2026)
- [Africa Check: Nairobi dentists](https://africacheck.org/infofinder/explore-facts/how-many-registered-dentists-are-there-nairobi)
- [ITWeb: Safaricom and iXAfrica data centre](https://itweb.africa/article/safaricom-ixafrica-launch-east-africas-first-ai-ready-data-centre/wbrpO7g2XBLvDLZn)
- [Baxtel: Nairobi data centres](https://baxtel.com/data-center/nairobi)
- [Supabase: regions](https://supabase.com/docs/guides/platform/regions)
- [eNCA: Feb 2026 blackout](https://www.enca.com/news-top-stories/power-cut-hits-large-parts-kenya)
