import { PrismaClient, PreferredLanguage, AccountStatus, MaterialCategory, PriceSource, RecyclerAuthorizationStatus, PickupAvailability } from '@prisma/client';
import { randomBytes, scryptSync } from 'node:crypto';
const prisma = new PrismaClient();
const demoPasswordHash = `${Buffer.from('kabadiwala-demo-salt').toString('hex')}:${scryptSync('DemoPass123!', Buffer.from('kabadiwala-demo-salt'), 64).toString('hex')}`;
async function main() {
  if (process.env.APP_ENV !== 'testing' || process.env.NODE_ENV === 'production') {
    throw new Error('The development seed is restricted to APP_ENV=testing and cannot run against production.');
  }
  const adminEmail = process.env.ADMIN_SEED_EMAIL?.trim().toLowerCase();
  const adminPassword = process.env.ADMIN_SEED_PASSWORD;
  if (adminEmail && adminPassword) {
    const salt = randomBytes(16);
    const passwordHash = `${salt.toString('hex')}:${scryptSync(adminPassword, salt, 64).toString('hex')}`;
    await prisma.adminAccount.upsert({ where: { email: adminEmail }, update: { passwordHash, active: true, permissions: ['RECYCLER_REVIEW', 'RECYCLER_AUTHORIZATION', 'DISPUTE_RESOLUTION', 'PAYMENT_VERIFICATION', 'PRICE_MANAGEMENT', 'DATASET_EXPORT'] }, create: { email: adminEmail, passwordHash, displayName: 'Operations admin', permissions: ['RECYCLER_REVIEW', 'RECYCLER_AUTHORIZATION', 'DISPUTE_RESOLUTION', 'PAYMENT_VERIFICATION', 'PRICE_MANAGEMENT', 'DATASET_EXPORT'] } });
  } else if (adminEmail || adminPassword) {
    throw new Error('ADMIN_SEED_EMAIL and ADMIN_SEED_PASSWORD must be provided together');
  }
  const existingCollector = await prisma.collector.findFirst({ where: { phone: '9876543210' } });
  const demoCollector = existingCollector
    ? await prisma.collector.update({ where: { id: existingCollector.id }, data: { email: 'dev-collector@kabadiwala.example' } })
    : await prisma.collector.create({ data: { phone: '9876543210', email: 'dev-collector@kabadiwala.example', preferredLanguage: PreferredLanguage.HINDI, areaName: 'Development Area', accountStatus: AccountStatus.ACTIVE } });
  // The collector profile is unique per account. A previous partial seed or an
  // app-created account can already own this profile under a different email,
  // so adopt that account instead of failing with a unique-constraint error.
  const existingCollectorUser = await prisma.user.findFirst({ where: { collectorProfileId: demoCollector.id } })
    ?? await prisma.user.findFirst({ where: { email: 'dev-collector@kabadiwala.example' } });
  if (existingCollectorUser) await prisma.user.update({ where: { id: existingCollectorUser.id }, data: { collectorProfileId: demoCollector.id, role: 'COLLECTOR', preferredLanguage: PreferredLanguage.HINDI, accountStatus: AccountStatus.ACTIVE } });
  else await prisma.user.create({ data: { email: 'dev-collector@kabadiwala.example', passwordHash: demoPasswordHash, role: 'COLLECTOR', preferredLanguage: PreferredLanguage.HINDI, collectorProfileId: demoCollector.id, accountStatus: AccountStatus.ACTIVE } });
  const ensureCollectorAccount = async (email: string, phone: string, role: 'HOUSEHOLD' | 'COLLECTOR', displayName: string, areaName: string) => {
    const found = await prisma.collector.findFirst({ where: { phone } });
    const profile = found ? await prisma.collector.update({ where: { id: found.id }, data: { email, displayName, areaName, accountStatus: AccountStatus.ACTIVE } }) : await prisma.collector.create({ data: { phone, email, displayName, areaName, preferredLanguage: PreferredLanguage.ENGLISH, accountStatus: AccountStatus.ACTIVE } });
    // The collector profile is unique per account, so prefer the account that
    // already owns this profile and fall back to the fixture email.
    const existing = await prisma.user.findFirst({ where: { collectorProfileId: profile.id } })
      ?? await prisma.user.findFirst({ where: { email } });
    if (existing) await prisma.user.update({ where: { id: existing.id }, data: { role, collectorProfileId: profile.id, preferredLanguage: PreferredLanguage.ENGLISH, accountStatus: AccountStatus.ACTIVE } });
    else await prisma.user.create({ data: { email, passwordHash: demoPasswordHash, role, preferredLanguage: PreferredLanguage.ENGLISH, collectorProfileId: profile.id, accountStatus: AccountStatus.ACTIVE } });
    return profile;
  };
  // Safe validation fixtures: isolated accounts used by the demonstration
  // runbook, never selected by application code or exposed as defaults.
  const householdA = await ensureCollectorAccount('household-a@kabadiwala.example', '9876543201', 'HOUSEHOLD', 'Household A', 'Kothrud, Pune');
  const householdB = await ensureCollectorAccount('household-b@kabadiwala.example', '9876543202', 'HOUSEHOLD', 'Household B', 'Baner, Pune');
  await ensureCollectorAccount('kabadiwala-b@kabadiwala.example', '9876543203', 'COLLECTOR', 'Kabadiwala B', 'Aundh, Pune');
  // Stable, isolated fixtures for the manual supply-chain runbook. Newspaper
  // is represented as OTHER because MaterialCategory has no PAPER value yet.
  await prisma.householdListing.upsert({ where: { id: 'dev-household-a-pet' }, update: { householdId: householdA.id, materialCategory: MaterialCategory.PLASTIC, estimatedWeight: 12, condition: 'INTACT', estimatedPriceMin: 240, estimatedPriceMax: 420, areaName: 'Kothrud, Pune', status: 'POSTED' }, create: { id: 'dev-household-a-pet', householdId: householdA.id, materialCategory: MaterialCategory.PLASTIC, estimatedWeight: 12, condition: 'INTACT', estimatedPriceMin: 240, estimatedPriceMax: 420, areaName: 'Kothrud, Pune', status: 'POSTED' } });
  await prisma.householdListing.upsert({ where: { id: 'dev-household-a-newspaper' }, update: { householdId: householdA.id, materialCategory: MaterialCategory.OTHER, estimatedWeight: 8, condition: 'INTACT', estimatedPriceMin: 120, estimatedPriceMax: 280, areaName: 'Kothrud, Pune', status: 'POSTED' }, create: { id: 'dev-household-a-newspaper', householdId: householdA.id, materialCategory: MaterialCategory.OTHER, estimatedWeight: 8, condition: 'INTACT', estimatedPriceMin: 120, estimatedPriceMax: 280, areaName: 'Kothrud, Pune', status: 'POSTED' } });
  await prisma.householdListing.upsert({ where: { id: 'dev-household-b-metal' }, update: { householdId: householdB.id, materialCategory: MaterialCategory.COPPER, estimatedWeight: 10, condition: 'DAMAGED', estimatedPriceMin: 4560, estimatedPriceMax: 5320, areaName: 'Baner, Pune', status: 'POSTED' }, create: { id: 'dev-household-b-metal', householdId: householdB.id, materialCategory: MaterialCategory.COPPER, estimatedWeight: 10, condition: 'DAMAGED', estimatedPriceMin: 4560, estimatedPriceMax: 5320, areaName: 'Baner, Pune', status: 'POSTED' } });
  // Development prices cover every material the Android catalogue requests.
  // These are clearly marked test fixtures and are not current-market claims.
  const priceFixtures = [
    { materialCategory: MaterialCategory.CRT, priceMin: 1500, priceMax: 2200, marketPrice: 1800 },
    { materialCategory: MaterialCategory.LCD_PANEL, priceMin: 1800, priceMax: 2600, marketPrice: 2200 },
    { materialCategory: MaterialCategory.PCB, priceMin: 2100, priceMax: 2700, marketPrice: 2400 },
    { materialCategory: MaterialCategory.CABLE, priceMin: 500, priceMax: 700, marketPrice: 600 },
    { materialCategory: MaterialCategory.COPPER, priceMin: 650, priceMax: 800, marketPrice: 720 },
    { materialCategory: MaterialCategory.BATTERY, priceMin: 80, priceMax: 130, marketPrice: 105 },
    { materialCategory: MaterialCategory.MOTOR, priceMin: 180, priceMax: 280, marketPrice: 230 },
    { materialCategory: MaterialCategory.MAGNET, priceMin: 150, priceMax: 240, marketPrice: 195 },
    { materialCategory: MaterialCategory.PLASTIC, priceMin: 25, priceMax: 40, marketPrice: 32 },
    { materialCategory: MaterialCategory.OTHER, priceMin: 15, priceMax: 25, marketPrice: 20 }
  ];
  const rows = ['Mumbai', 'Pune'].flatMap(city => priceFixtures.map(row => ({ id: `dev-${city.toLowerCase()}-${row.materialCategory.toLowerCase()}`, city, ...row })));
  for (const row of rows) { const effectiveAt = new Date(); const { id, ...values } = row; const price = await prisma.price.upsert({ where: { id }, update: { ...values, source: PriceSource.SYSTEM, effectiveAt, ingestedAt: effectiveAt }, create: { id, ...values, source: PriceSource.SYSTEM, effectiveAt, ingestedAt: effectiveAt, historicalAverage: row.marketPrice } }); for (let i = 3; i >= 0; i--) { const historyEffectiveAt = new Date(Date.now() - i * 7 * 86400000); const marketPrice = row.marketPrice - i * 25; await prisma.priceHistory.upsert({ where: { id: `${row.id}-h${i}` }, update: { marketPrice, effectiveAt: historyEffectiveAt, ingestedAt: historyEffectiveAt }, create: { id: `${row.id}-h${i}`, priceId: price.id, materialCategory: row.materialCategory, city: row.city, priceMin: row.priceMin, priceMax: row.priceMax, marketPrice, source: PriceSource.SYSTEM, effectiveAt: historyEffectiveAt, ingestedAt: historyEffectiveAt } }); } }
  await prisma.rewardProgram.upsert({ where: { programKey: 'monthly-collector-25kg' }, update: { active: true, title: 'Monthly collection bonus', description: 'Complete 25 kg of verified paid handovers this month to unlock a platform bonus.', rewardType: 'CASH_BONUS', thresholdKg: 25, thresholdRupees: null, rewardAmount: 150, terms: 'Applies once per calendar month after a verified paid handover.', startsAt: new Date('2025-01-01T00:00:00.000Z') }, create: { programKey: 'monthly-collector-25kg', title: 'Monthly collection bonus', description: 'Complete 25 kg of verified paid handovers this month to unlock a platform bonus.', rewardType: 'CASH_BONUS', thresholdKg: 25, rewardAmount: 150, terms: 'Applies once per calendar month after a verified paid handover.', startsAt: new Date('2025-01-01T00:00:00.000Z') } });
  await prisma.governmentScheme.upsert({ where: { slug: 'pm-svanidhi-guidance' }, update: { active: true, lastVerifiedAt: new Date() }, create: { slug: 'pm-svanidhi-guidance', title: 'Small business support guidance', description: 'A guided checklist for informal workers exploring official small-business support. Verify details with the government source before applying.', eligibilityRules: { workType: ['COLLECTOR', 'RECYCLER'] }, requiredDocuments: ['Identity proof', 'Bank account details', 'Address proof'], sourceUrl: 'https://www.myscheme.gov.in/', lastVerifiedAt: new Date(), supportedLanguages: ['en', 'hi', 'mr'], active: true } });
  await prisma.diyActivity.upsert({ where: { slug: 'cable-organizer' }, update: { active: true }, create: { slug: 'cable-organizer', title: 'Cable organiser', description: 'Turn safe, unplugged cable lengths into a simple organiser for a drawer or work table.', materials: ['Clean insulated cables', 'Cardboard strip', 'Tape'], steps: ['Check that cables are unplugged and have no exposed wire.', 'Bundle short lengths around a cardboard strip.', 'Secure the bundle with tape and label it.'], safetyWarnings: ['Do not use cables with exposed copper.', 'Never cut or open batteries, CRTs, capacitors, or sealed electronics.'], difficulty: 'Easy', minutes: 15, supportedLanguages: ['en', 'hi', 'mr'], active: true } });
  const recyclers=[{id:'dev-recycler-mumbai',name:'Development Recycler Mumbai',areaName:'Mumbai',address:'Development address, Mumbai',latitude:19.076,longitude:72.8777,materials:[MaterialCategory.PCB,MaterialCategory.CABLE],rate:2500,availability:PickupAvailability.TODAY,max:25,rating:4.4},{id:'dev-recycler-pune',name:'Development Recycler Pune',areaName:'Pune',address:'Development address, Pune',latitude:18.5204,longitude:73.8567,materials:[MaterialCategory.PCB,MaterialCategory.PLASTIC],rate:2350,availability:PickupAvailability.THIS_WEEK,max:50,rating:null},{id:'dev-recycler-metal',name:'Development Recycler Metalworks',areaName:'Pune',address:'Development address, Pune',latitude:18.56,longitude:73.78,materials:[MaterialCategory.COPPER],rate:535,availability:PickupAvailability.FLEXIBLE,max:100,rating:4.6},{id:'dev-recycler-pending',name:'Development Pending Recycler',areaName:'Mumbai',address:'Development address, Mumbai',latitude:19.1,longitude:72.9,materials:[MaterialCategory.PCB],rate:2800,availability:PickupAvailability.FLEXIBLE,max:10,rating:null}];
  for(const r of recyclers){const recycler=await prisma.recycler.upsert({where:{id:r.id},update:{name:r.name,areaName:r.areaName,address:r.address,latitude:r.latitude,longitude:r.longitude,authorizationStatus:r.id.endsWith('pending')?RecyclerAuthorizationStatus.PENDING:RecyclerAuthorizationStatus.VERIFIED,pickupAvailability:r.availability,pickupIncluded:r.availability !== PickupAvailability.FLEXIBLE,logisticsCostPerKm:r.availability === PickupAvailability.FLEXIBLE ? 12 : 0,pickupFee:r.availability === PickupAvailability.FLEXIBLE ? 0 : 0,maxPickupDistanceKm:r.max,operatingHours:{monday:{open:'09:00',close:'18:00'}},rating:r.rating,reviewCount:r.rating?12:0},create:{id:r.id,name:r.name,areaName:r.areaName,address:r.address,latitude:r.latitude,longitude:r.longitude,authorizationStatus:r.id.endsWith('pending')?RecyclerAuthorizationStatus.PENDING:RecyclerAuthorizationStatus.VERIFIED,pickupAvailability:r.availability,pickupIncluded:r.availability !== PickupAvailability.FLEXIBLE,logisticsCostPerKm:r.availability === PickupAvailability.FLEXIBLE ? 12 : 0,pickupFee:r.availability === PickupAvailability.FLEXIBLE ? 0 : 0,maxPickupDistanceKm:r.max,operatingHours:{monday:{open:'09:00',close:'18:00'}},rating:r.rating,reviewCount:r.rating?12:0}});await prisma.recyclerMaterial.deleteMany({where:{recyclerId:r.id}});await prisma.recyclerRate.deleteMany({where:{recyclerId:r.id}});for(const m of r.materials)await prisma.recyclerMaterial.create({data:{recyclerId:recycler.id,category:m,subcategories:[],acceptedGrades:['UNSPECIFIED'],minAcceptableWeight:0.1,maxAcceptableWeight:500}});for(const m of r.materials)await prisma.recyclerRate.create({data:{recyclerId:recycler.id,materialCategory:m,pricePerKg:r.rate}});}
  const verifiedDemoRecycler = await prisma.recycler.findUnique({ where: { id: 'dev-recycler-pune' } });
  if (verifiedDemoRecycler) {
    const existingRecyclerUser = await prisma.user.findFirst({ where: { email: 'dev-recycler@kabadiwala.example' } });
    if (existingRecyclerUser) await prisma.user.update({ where: { id: existingRecyclerUser.id }, data: { recyclerProfileId: verifiedDemoRecycler.id, role: 'RECYCLER', preferredLanguage: PreferredLanguage.ENGLISH, accountStatus: AccountStatus.ACTIVE } });
    else await prisma.user.create({ data: { email: 'dev-recycler@kabadiwala.example', passwordHash: demoPasswordHash, role: 'RECYCLER', preferredLanguage: PreferredLanguage.ENGLISH, recyclerProfileId: verifiedDemoRecycler.id, accountStatus: AccountStatus.ACTIVE } });
  }
  const metalRecycler = await prisma.recycler.findUnique({ where: { id: 'dev-recycler-metal' } });
  if (metalRecycler) {
    const existingMetalUser = await prisma.user.findFirst({ where: { email: 'recycler-b@kabadiwala.example' } });
    if (existingMetalUser) await prisma.user.update({ where: { id: existingMetalUser.id }, data: { recyclerProfileId: metalRecycler.id, role: 'RECYCLER', accountStatus: AccountStatus.ACTIVE } });
    else await prisma.user.create({ data: { email: 'recycler-b@kabadiwala.example', passwordHash: demoPasswordHash, role: 'RECYCLER', recyclerProfileId: metalRecycler.id, accountStatus: AccountStatus.ACTIVE } });
  }
  console.log('Development collector and price seed complete. Price data is test data, not current market claims.');
}
main().catch((error) => { console.error(error); process.exitCode = 1; }).finally(() => prisma.$disconnect());
