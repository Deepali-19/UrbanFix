# Urban Fix

Urban Fix is an Android admin application for municipal complaint management. It helps city teams review incoming complaints, assign field officers, monitor resolution progress, manage staff approvals, and track issue hotspots on a live map.

## Overview

This project is built for government and civic administration workflows, not general public browsing. The app focuses on official-side operations through role-based access for Super Admins, Department Admins, and Field Officers.

Urban Fix uses Firebase for authentication, realtime data, and messaging; Appwrite for official ID proof storage; Google Maps for geo-visualization; and optional AI services for complaint assistance and image authenticity checks.

## Key Features

- Role-based access for `Super Admin`, `Department Admin`, and `Field Officer`
- Secure admin signup with official ID proof upload
- Approval workflow for staff accounts
- Live complaint dashboard with analytics and charts
- Complaint filtering by status, priority, department, and date range
- Complaint detail screen with assignment, validation, ETA, and status updates
- Map view with density, department, and marker modes
- Alerts and broadcast notifications for admin teams
- Firebase Cloud Messaging support
- English and Hindi language support
- Dark mode and profile preferences
- AI-generated resolution suggestions using Gemini
- Image authenticity checks using Sightengine

## Roles

- `Super Admin`: city-wide visibility, approvals, alerts, and management access
- `Department Admin`: department-scoped complaint monitoring and assignment
- `Field Officer`: assigned complaint execution and status updates

## Tech Stack

- `Kotlin`
- `Android ViewBinding`
- `Firebase Authentication`
- `Firebase Realtime Database`
- `Firebase Cloud Messaging`
- `Firebase Analytics`
- `Appwrite Storage`
- `Google Maps SDK`
- `Google Maps Utils / Heatmaps`
- `MPAndroidChart`
- `Glide`
- `Lottie`
- `Gemini API` for AI suggestions
- `Sightengine API` for image authenticity analysis
- `Firebase Functions + Nodemailer` for approval email flows

## Approval Workflow

Urban Fix supports two approval routes:

- `City Super Admin approval` inside the app
- `Root email approval` through Firebase Hosting / Functions for bootstrap or fallback scenarios

Approved users receive employee IDs generated from city, role, and department context.

## Project Structure

- `app/` Android application
- `functions/` Firebase Functions for approval email handling
- `hosting/approval/` approval web page assets

## Local Setup

### Prerequisites

- Android Studio
- JDK 11
- Firebase project
- Appwrite project and storage bucket
- Google Maps API key
- Optional Gemini and Sightengine credentials
- Node.js 20 for Firebase Functions

### Required configuration

Create your local secrets from `secrets.properties.example` and provide values for:

- `MAPS_API_KEY`
- `APPWRITE_ENDPOINT`
- `APPWRITE_PROJECT_ID`
- `APPWRITE_BUCKET_ID`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `SIGHTENGINE_API_USER`
- `SIGHTENGINE_API_SECRET`
- `ROOT_APPROVAL_EMAIL`
- `FCM_SERVER_KEY`
- `SMTP_EMAIL`
- `SMTP_APP_PASSWORD`
- `APPROVAL_ACTION_BASE_URL`

Also add your Firebase Android config file separately because it is not included in the repository.

