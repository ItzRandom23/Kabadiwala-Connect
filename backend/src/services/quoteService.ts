import type { PrismaClient } from '@prisma/client'; import { AppError } from '../utils/errors.js'; import { assertLotTransition } from './lotStateMachine.js'; import { canonicalWeight } from './lotService.js';
export class QuoteService {
  constructor(private readonly db: PrismaClient) {}
  private async findRequest(id:string){return this.db.quoteRequest.findUnique({where:{id},include:{lot:true,recycler:true}});}
  private collectorRecyclerView(recycler: any) {
    if (!recycler) return undefined;
    return {
      id: recycler.id,
      name: recycler.name,
      facilityLocation: { latitude: null, longitude: null, address: null, areaName: recycler.areaName },
      authorizationStatus: recycler.authorizationStatus,
      authorizationDetails: { authority: recycler.authorizationAuthority, type: recycler.authorizationType, verifiedAt: recycler.verifiedAt, validUntil: recycler.authorizationValidUntil },
      materialsAccepted: (recycler.materials ?? []).map((material: any) => ({ category: material.category, subcategories: material.subcategories, minAcceptableWeight: material.minAcceptableWeight, maxAcceptableWeight: material.maxAcceptableWeight })),
      rates: (recycler.rates ?? []).map((rate: any) => ({ materialCategory: rate.materialCategory, pricePerKg: rate.pricePerKg, unit: rate.unit, qualityStatus: rate.qualityStatus, effectiveAt: rate.effectiveAt, updatedAt: rate.updatedAt })),
      pickupAvailability: recycler.pickupAvailability ?? 'FLEXIBLE',
      serviceArea: { maxPickupDistanceKm: recycler.maxPickupDistanceKm },
      operatingHours: recycler.operatingHours,
      averageHandoverTime: recycler.averageHandoverTime,
      rating: recycler.rating,
      reviewCount: recycler.reviewCount,
      completedHandovers: recycler._count?.handovers ?? null,
      lastUpdated: recycler.updatedAt
    };
  }
  private collectorQuoteView(quote: any) {
    return {
      id: quote.id,
      quoteRequestId: quote.quoteRequestId,
      lotId: quote.lotId,
      recyclerId: quote.recyclerId,
      pricePerKg: quote.pricePerKg,
      totalQuotedPrice: quote.totalQuotedPrice,
      validUntil: quote.validUntil,
      status: quote.status,
      recyclerNotes: quote.recyclerNotes,
      comparison: quote.comparison,
      anomaly: quote.anomaly,
      createdAt: quote.createdAt,
      sentAt: quote.sentAt,
      respondedAt: quote.respondedAt,
      acceptedAt: quote.acceptedAt,
      rejectedAt: quote.rejectedAt,
      recycler: this.collectorRecyclerView(quote.recycler)
    };
  }
  private recyclerRequestView(q:any){return {id:q.id,lotId:q.lotId,materialCategory:q.materialCategory,weight:q.weight,estimatedValue:q.estimatedValue,quotedPrice:q.lot?.quotedPrice??null,collectionLocation:{areaName:q.lot?.collectionAreaName??'Area to be confirmed',precision:'APPROXIMATE'},createdAt:q.createdAt,expiresAt:q.expiresAt,status:q.status,lot:q.lot?{id:q.lot.id,materialCategory:q.lot.materialCategory,materialSubcategory:q.lot.materialSubcategory,condition:q.lot.condition,weight:q.lot.weight,estimatedValue:q.lot.estimatedValue,quotedPrice:q.lot.quotedPrice,collectionAreaName:q.lot.collectionAreaName,status:q.lot.status,createdAt:q.lot.createdAt}:undefined};}
  async requestQuote(collectorId:string,lotId:string,recyclerId:string){const lot=await this.db.lot.findFirst({where:{id:lotId,collectorId}});if(!lot)throw new AppError('NOT_FOUND','Lot not found',404,{code:'LOT_NOT_FOUND'});const now=new Date();if(lot.status==='QUOTE_RECEIVED'){await this.db.quote.updateMany({where:{lotId,status:'SENT',validUntil:{lte:now}},data:{status:'EXPIRED',respondedAt:now}});const accepted=await this.db.quote.count({where:{lotId,status:'ACCEPTED'}});if(accepted>0)throw new AppError('CONFLICT','This lot already has an accepted quote',409,{code:'LOT_HAS_ACCEPTED_QUOTE'});}if(!['CREATED','QUOTE_REQUESTED','QUOTE_RECEIVED'].includes(lot.status))throw new AppError('CONFLICT','Lot is not eligible for quote',409,{code:'LOT_NOT_ELIGIBLE_FOR_QUOTE'});const weightKg=canonicalWeight(lot.weight,lot.weightUnit);const r=await this.db.recycler.findUnique({where:{id:recyclerId},include:{materials:true}});if(!r)throw new AppError('NOT_FOUND','Recycler not found',404,{code:'RECYCLER_NOT_FOUND'});if(r.authorizationStatus!=='VERIFIED')throw new AppError('CONFLICT','Recycler is not verified',409,{code:'RECYCLER_NOT_VERIFIED'});if(!r.materials.some(m=>m.category===lot.materialCategory))throw new AppError('CONFLICT','Material is not supported',409,{code:'RECYCLER_MATERIAL_NOT_SUPPORTED'});const old=await this.db.quoteRequest.findFirst({where:{collectorId,lotId,recyclerId,status:'PENDING'}});if(old){if(old.expiresAt>now)return old;await this.db.quoteRequest.updateMany({where:{id:old.id,status:'PENDING'},data:{status:'EXPIRED'}});}try{return await this.db.$transaction(async tx=>{const q=await tx.quoteRequest.create({data:{lotId,collectorId,recyclerId,materialCategory:lot.materialCategory,weight:weightKg,estimatedValue:lot.estimatedValue,collectionLocation:{latitude:lot.collectionLatitude,longitude:lot.collectionLongitude,areaName:lot.collectionAreaName},expiresAt:new Date(Date.now()+86400000)}});if(lot.status==='CREATED')assertLotTransition(lot.status,'QUOTE_REQUESTED');if(lot.status==='CREATED'||lot.status==='QUOTE_REQUESTED')await tx.lot.update({where:{id:lotId},data:{status:'QUOTE_REQUESTED'}});await tx.quoteAudit.create({data:{actorId:collectorId,actorRole:'COLLECTOR',event:'QUOTE_REQUESTED',resourceId:q.id,metadata:{lotId,recyclerId}}});return q;});}catch(error:any){if(error?.code==='P2002'){const retry=await this.db.quoteRequest.findFirst({where:{collectorId,lotId,recyclerId,status:'PENDING'}});if(retry)return retry;}throw error;}}
  async pending(collectorId:string,lotId?:string){const requests=await this.db.quoteRequest.findMany({where:{collectorId,...(lotId?{lotId}:{})},include:{quotes:{include:{recycler:true}}},orderBy:{createdAt:'desc'}});return requests.flatMap(request=>request.quotes.map(quote=>this.collectorQuoteView(quote)));}
  async detail(id:string,actorId:string,role:'COLLECTOR'|'RECYCLER'){
    if (role === 'RECYCLER') {
      const request = await this.findRequest(id);
      if (!request || request.recyclerId !== actorId) throw new AppError('NOT_FOUND','Quote request not found',404,{code:'QUOTE_REQUEST_NOT_FOUND'});
      return this.recyclerRequestView(request);
    }
    const quote = await this.db.quote.findUnique({ where: { id }, include: { quoteRequest: true, recycler: { include: { materials: true, rates: true, _count: { select: { handovers: true } } } } } });
    if (!quote || quote.quoteRequest.collectorId !== actorId) throw new AppError('NOT_FOUND','Quote not found',404,{code:'QUOTE_NOT_FOUND'});
    return this.collectorQuoteView(quote);
  }
  async submit(recyclerId:string,p:{quoteRequestId:string;pricePerKg:number;validUntil?:string;recyclerNotes?:string}){const q=await this.findRequest(p.quoteRequestId);if(!q||q.recyclerId!==recyclerId)throw new AppError('NOT_FOUND','Quote request not found',404,{code:'QUOTE_REQUEST_NOT_FOUND'});if(q.recycler.authorizationStatus!=='VERIFIED')throw new AppError('CONFLICT','Recycler is not verified',409,{code:'RECYCLER_NOT_VERIFIED'});if(q.status==='PENDING'&&q.expiresAt<=new Date()){await this.db.quoteRequest.updateMany({where:{id:q.id,status:'PENDING'},data:{status:'EXPIRED'}});throw new AppError('CONFLICT','Quote request has expired',409,{code:'QUOTE_REQUEST_EXPIRED'});}if(q.status!=='PENDING'||q.lot.status==='CANCELLED')throw new AppError('CONFLICT','Quote request is not actionable',409,{code:'QUOTE_NOT_ACTIONABLE'});if(!Number.isFinite(p.pricePerKg)||p.pricePerKg<=0||p.pricePerKg>=1000000)throw new AppError('VALIDATION_ERROR','Invalid quote price',422,{code:'INVALID_QUOTE_PRICE'});const until=p.validUntil?new Date(p.validUntil):new Date(Date.now()+86400000);if(Number.isNaN(until.getTime())||until<=new Date()||until>new Date(Date.now()+86400000))throw new AppError('VALIDATION_ERROR','Invalid quote validity',422,{code:'INVALID_QUOTE_VALIDITY'});const market=await this.db.price.findFirst({where:{materialCategory:q.lot.materialCategory},orderBy:{effectiveAt:'desc'}});const anomaly=!!market&&p.pricePerKg>=market.marketPrice*2;const weightKg=canonicalWeight(q.lot.weight,q.lot.weightUnit);if(q.lot.status==='QUOTE_REQUESTED')assertLotTransition(q.lot.status,'QUOTE_RECEIVED');return this.db.$transaction(async tx=>{const claimed=await tx.quoteRequest.updateMany({where:{id:q.id,status:'PENDING'},data:{status:'ACCEPTED'}});if(!claimed.count)throw new AppError('CONFLICT','Quote request is no longer actionable',409,{code:'QUOTE_NOT_ACTIONABLE'});const quote=await tx.quote.create({data:{quoteRequestId:q.id,lotId:q.lotId,recyclerId,pricePerKg:p.pricePerKg,totalQuotedPrice:Number((p.pricePerKg*weightKg).toFixed(2)),validUntil:until,recyclerNotes:p.recyclerNotes,anomaly,comparison:market?(p.pricePerKg<market.marketPrice*.95?'BELOW_MARKET':p.pricePerKg>market.marketPrice*1.05?'ABOVE_MARKET':'FAIR'):null}});await tx.lot.update({where:{id:q.lotId},data:{status:'QUOTE_RECEIVED'}});await tx.quoteAudit.create({data:{actorId:recyclerId,actorRole:'RECYCLER',event:'QUOTE_SUBMITTED',resourceId:quote.id,metadata:{quoteRequestId:q.id}}});return quote;});}
  async action(id:string,collectorId:string,accept:boolean){const q=await this.db.quote.findUnique({where:{id},include:{quoteRequest:true,lot:true}});if(!q||q.quoteRequest.collectorId!==collectorId)throw new AppError('NOT_FOUND','Quote not found',404,{code:'QUOTE_NOT_FOUND'});const now=new Date();if(q.status!=='SENT'||q.validUntil<=now){if(q.status==='SENT'&&q.validUntil<=now)await this.db.quote.updateMany({where:{id,status:'SENT'},data:{status:'EXPIRED',respondedAt:now}});throw new AppError('CONFLICT','Quote is expired or not actionable',409,{code:'QUOTE_EXPIRED'});}if(accept)assertLotTransition(q.lot.status,'COLLECTOR_CONFIRMED');return this.db.$transaction(async tx=>{if(accept){const claimed=await tx.quote.updateMany({where:{id,status:'SENT',validUntil:{gt:new Date()}},data:{status:'ACCEPTED',acceptedAt:new Date(),respondedAt:new Date()}});if(!claimed.count)throw new AppError('CONFLICT','Quote is no longer actionable',409,{code:'QUOTE_ALREADY_ACTIONED'});await tx.quote.updateMany({where:{lotId:q.lotId,status:'SENT',id:{not:id}},data:{status:'REJECTED',rejectedAt:new Date(),respondedAt:new Date()}});const lot=await tx.lot.updateMany({where:{id:q.lotId,status:'QUOTE_RECEIVED'},data:{status:'COLLECTOR_CONFIRMED'}});if(!lot.count)throw new AppError('CONFLICT','Lot is no longer actionable',409,{code:'LOT_ALREADY_ACTIONED'});await tx.conversation.upsert({where:{lotId_collectorId_recyclerId:{lotId:q.lotId,collectorId,recyclerId:q.recyclerId}},update:{quoteId:id,status:'OPEN'},create:{lotId:q.lotId,quoteId:id,collectorId,recyclerId:q.recyclerId}});return tx.quote.findUniqueOrThrow({where:{id}});}const rejected=await tx.quote.updateMany({where:{id,status:'SENT'},data:{status:'REJECTED',rejectedAt:new Date(),respondedAt:new Date()}});if(!rejected.count)throw new AppError('CONFLICT','Quote is no longer actionable',409,{code:'QUOTE_ALREADY_ACTIONED'});await tx.quoteRequest.updateMany({where:{id:q.quoteRequestId,status:'ACCEPTED'},data:{status:'REJECTED'}});const remaining=await tx.quote.count({where:{lotId:q.lotId,status:{in:['SENT','ACCEPTED']}}});if(remaining===0){await tx.lot.updateMany({where:{id:q.lotId,status:'QUOTE_RECEIVED'},data:{status:'QUOTE_REQUESTED'}});await tx.quoteAudit.create({data:{actorId:collectorId,actorRole:'COLLECTOR',event:'QUOTE_REJECTED_REOPENED',resourceId:id,metadata:{lotId:q.lotId}}});}return tx.quote.findUniqueOrThrow({where:{id}});});}
  async requestQuoteBatch(collectorId: string, lotId: string, recyclerIds: string[]) {
    const uniqueIds = [...new Set(recyclerIds.map(id => id.trim()).filter(Boolean))].slice(0, 10);
    const requested: any[] = [];
    const rejected: Array<{ recyclerId: string; code: string }> = [];
    for (const recyclerId of uniqueIds) {
      try {
        requested.push(await this.requestQuote(collectorId, lotId, recyclerId));
      } catch (error: any) {
        rejected.push({ recyclerId, code: error?.details?.code ?? error?.code ?? 'QUOTE_REQUEST_REJECTED' });
      }
    }
    if (!requested.length) throw new AppError('CONFLICT', 'No eligible recycler accepted the quote request', 409, { code: 'NO_ELIGIBLE_RECYCLER', rejected });
    return { requested, rejected };
  }
  async recyclerRequests(recyclerId:string){const rows=await this.db.quoteRequest.findMany({where:{recyclerId},include:{lot:true},orderBy:{createdAt:'desc'}});return rows.map(row=>this.recyclerRequestView(row));}
}
