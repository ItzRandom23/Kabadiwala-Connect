CREATE TYPE "PreferredLanguage" AS ENUM ('MARATHI', 'HINDI');
CREATE TYPE "AccountStatus" AS ENUM ('ACTIVE', 'SUSPENDED', 'DELETED');

CREATE TABLE "Collector" (
    "id" TEXT NOT NULL,
    "phone" VARCHAR(10) NOT NULL,
    "preferredLanguage" "PreferredLanguage" NOT NULL,
    "latitude" DECIMAL(9,6),
    "longitude" DECIMAL(9,6),
    "areaName" VARCHAR(160) NOT NULL,
    "accountStatus" "AccountStatus" NOT NULL DEFAULT 'ACTIVE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastLoginAt" TIMESTAMP(3),
    CONSTRAINT "Collector_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "Collector_phone_key" ON "Collector"("phone");
CREATE INDEX "Collector_accountStatus_idx" ON "Collector"("accountStatus");
CREATE INDEX "Collector_areaName_idx" ON "Collector"("areaName");
