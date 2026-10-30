import type {
  TravelExpenseType,
  TravelItineraryType,
  TravelPlan,
  TravelPlanCompanion,
  TravelPlanDetail,
  TravelPlanExpense,
  TravelPlanItinerary,
} from '@/api/modules/tool'

export type TravelCardTone = 'blue' | 'green' | 'orange' | 'violet'

export type TravelTimelineEntry =
  | {
      kind: 'item'
      tone: TravelCardTone
      title: string
      description: string
    }
  | {
      kind: 'transfer'
      text: string
    }

export type TravelRouteEditorItem = {
  id: number
  order: number
  tone: TravelCardTone
  type: TravelItineraryType
  title: string
  time: string
  description: string
  actionLabel: string
}

export type TravelExpenseEditorItem = {
  id: number
  title: string
  note: string
  tags: string[]
  amount: number
}

export type TravelDemoDay = {
  id: number
  dayIndex: number
  label: string
  title: string
  routeCountText: string
  overviewEntries: TravelTimelineEntry[]
  routeItems: TravelRouteEditorItem[]
  expenses: TravelExpenseEditorItem[]
}

export type TravelDemoPlan = {
  id: number
  cardTitle: string
  cardDate: string
  heading: string
  meta: string
  totalExpenseAmount: number
  perPersonExpenseAmount: number
  overviewHint: string
  expenseHint: string
  overviewExpenseRows: Array<{
    title: string
    note: string
    amount: number
  }>
  companions: TravelPlanCompanion[]
  days: TravelDemoDay[]
  searchResults: Array<{
    id: string
    title: string
    address: string
  }>
}

function itineraryToApiItem(dayId: number, item: TravelRouteEditorItem): TravelPlanItinerary {
  return {
    id: item.id,
    travelPlanDayId: dayId,
    type: item.type,
    title: item.title,
    poiName: item.title,
    poiId: String(item.id),
    address: item.description,
    longitude: null,
    latitude: null,
    startTime: null,
    remark: null,
    sortOrder: item.order,
    createdAt: '2026-03-01T00:00:00',
    updatedAt: '2026-03-01T00:00:00',
  }
}

function expenseToApiItem(planId: number, dayId: number, item: TravelExpenseEditorItem): TravelPlanExpense {
  return {
    id: item.id,
    travelPlanId: planId,
    travelPlanDayId: dayId,
    type: inferExpenseType(item.tags),
    title: item.title,
    amount: item.amount,
    payerContactId: null,
    payerContactName: null,
    remark: item.note,
    sortOrder: item.id,
    createdAt: '2026-03-01T00:00:00',
    updatedAt: '2026-03-01T00:00:00',
  }
}

function inferExpenseType(tags: string[]): TravelExpenseType {
  if (tags.includes('交通')) {
    return 'transport'
  }
  if (tags.includes('景区')) {
    return 'scenic'
  }
  if (tags.includes('餐饮')) {
    return 'dining'
  }
  if (tags.includes('住宿')) {
    return 'accommodation'
  }
  return 'other'
}

export const travelDemoPlans: TravelDemoPlan[] = [
  {
    id: 1,
    cardTitle: '云南慢旅行',
    cardDate: '2026.04.12 - 2026.04.20',
    heading: '昆明 · 大理 · 丽江',
    meta: '2026.04.12 - 2026.04.20 · 9 天 8 晚 · 2 人同行',
    totalExpenseAmount: 3268,
    perPersonExpenseAmount: 1634,
    overviewHint: '3 天行程共记录 12 笔费用，默认按 2 人平摊。',
    expenseHint: '3 天行程共记录 12 笔费用，默认按 2 人平摊。',
    overviewExpenseRows: [
      { title: '早餐米线', note: '爱玲 · 2 人小摊', amount: 28 },
      { title: '大理站 → 古城打车', note: '交通', amount: 42 },
      { title: '洱海环线包车', note: '双人包车一整天', amount: 980 },
      { title: '菌锅晚餐', note: '阿哲代点，双人套餐', amount: 168 },
      { title: '龙龛码头咖啡', note: '双人饮品', amount: 58 },
      { title: '双廊旅拍门票', note: '景区 · 2 张', amount: 466 },
    ],
    companions: [
      {
        id: 1,
        travelPlanId: 1,
        contactId: 1,
        contactName: '阿哲',
        contactPhone: '138****1001',
        contactRemark: '同行拍照搭子',
        sortOrder: 1,
        createdAt: '2026-03-01T00:00:00',
        updatedAt: '2026-03-01T00:00:00',
      },
    ],
    days: [
      {
        id: 11,
        dayIndex: 1,
        label: 'Day 1',
        title: '昆明适应日',
        routeCountText: '4 个点位',
        overviewEntries: [
          { kind: 'item', tone: 'blue', title: '飞机 → 昆明寄存行李', description: '抵达长水机场后先办行李托运。' },
          { kind: 'transfer', text: '长水机场 → 翠湖公园' },
          { kind: 'item', tone: 'green', title: '1 翠湖公园', description: '先在湖边散步，白天行程尽量放轻松。' },
          { kind: 'transfer', text: '翠湖 → 南屏街' },
          { kind: 'item', tone: 'orange', title: '人民路午餐', description: '云南小锅米线，顺便观察城市节奏。' },
          { kind: 'transfer', text: '南屏街 → 酒店' },
          { kind: 'item', tone: 'violet', title: '昆明翠湖丽亭酒店', description: '1 晚大床房，晚上回酒店正式休息。' },
        ],
        routeItems: [
          {
            id: 101,
            order: 1,
            tone: 'blue',
            type: 'transport',
            title: '长水机场 → 酒店寄存行李',
            time: '08:40',
            description: '航班抵达后先寄存行李，预留调整状态时间。',
            actionLabel: '交通',
          },
          {
            id: 102,
            order: 2,
            tone: 'green',
            type: 'scenic',
            title: '翠湖公园',
            time: '10:20 - 12:00',
            description: '在湖边散步，先走轻松路线。',
            actionLabel: '景区',
          },
          {
            id: 103,
            order: 3,
            tone: 'orange',
            type: 'dining',
            title: '人民路午餐',
            time: '12:30 - 13:40',
            description: '云南过桥米线，尽量靠窗位。',
            actionLabel: '餐饮',
          },
          {
            id: 104,
            order: 4,
            tone: 'violet',
            type: 'accommodation',
            title: '昆明翠湖丽亭酒店',
            time: '20:10 - 18:50',
            description: '办理入住，回酒店休整准备第二天高铁。',
            actionLabel: '住宿',
          },
        ],
        expenses: [
          { id: 201, title: '机场巴士', note: '交通 · 单程', tags: ['交通'], amount: 25 },
          { id: 202, title: '午餐米线', note: '餐饮 · 双人', tags: ['餐饮'], amount: 56 },
        ],
      },
      {
        id: 12,
        dayIndex: 2,
        label: 'Day 2',
        title: '前往大理',
        routeCountText: '4 个点位',
        overviewEntries: [
          { kind: 'item', tone: 'blue', title: '大理站 → 民宿寄存行李', description: '08:40 到站后先把行李放进民宿。' },
          { kind: 'transfer', text: '民宿 → 大理古城' },
          { kind: 'item', tone: 'green', title: '1 大理古城', description: '10:00 - 12:00 · 从南门逛到博爱路，先熟悉片区。' },
          { kind: 'transfer', text: '古城 → 人民路午餐' },
          { kind: 'item', tone: 'orange', title: '人民路午餐', description: '12:30 - 13:40 · 白族菜 / 烤乳扇。' },
          { kind: 'transfer', text: '午餐 → 龙龛码头' },
          { kind: 'item', tone: 'green', title: '2 龙龛码头', description: '17:10 - 18:50 · 日落光线适合拍照。' },
          { kind: 'transfer', text: '龙龛码头 → 民宿' },
          { kind: 'item', tone: 'violet', title: '大理古城山海观景民宿', description: '连住 3 晚，晚上回民宿办理入住并安顿行李。' },
        ],
        routeItems: [
          {
            id: 111,
            order: 1,
            tone: 'blue',
            type: 'transport',
            title: '大理站 → 民宿寄存行李',
            time: '08:40',
            description: '站边行李寄存后先办入住，减少拎包移动。',
            actionLabel: '交通',
          },
          {
            id: 112,
            order: 2,
            tone: 'green',
            type: 'scenic',
            title: '大理古城',
            time: '10:00 - 12:00',
            description: '从南门走到博爱路，沿途慢慢拍照。',
            actionLabel: '景区',
          },
          {
            id: 113,
            order: 3,
            tone: 'orange',
            type: 'dining',
            title: '人民路午餐',
            time: '12:30 - 13:40',
            description: '白族菜 + 烤乳扇，预留 1 小时吃饭休息。',
            actionLabel: '餐饮',
          },
          {
            id: 114,
            order: 4,
            tone: 'green',
            type: 'scenic',
            title: '龙龛码头',
            time: '17:10 - 18:50',
            description: '日落前 40 分钟到，拍逆光和骑行道。',
            actionLabel: '景区',
          },
          {
            id: 115,
            order: 5,
            tone: 'violet',
            type: 'accommodation',
            title: '大理古城山海观景民宿',
            time: '20:30',
            description: '20:30 办理入住，连住 3 晚。',
            actionLabel: '住宿',
          },
        ],
        expenses: [
          { id: 221, title: '早餐米线', note: '爱玲 · 2 人小摊', tags: ['早餐', '餐饮'], amount: 28 },
          { id: 222, title: '大理站 → 古城打车', note: '交通', tags: ['交通'], amount: 42 },
          { id: 223, title: '龙龛码头咖啡', note: '景区 · 双杯', tags: ['景区', '餐饮'], amount: 58 },
          { id: 224, title: '民宿首晚房费', note: '2 人 / 2 间房', tags: ['住宿'], amount: 468 },
        ],
      },
      {
        id: 13,
        dayIndex: 3,
        label: 'Day 3',
        title: '洱海环线',
        routeCountText: '4 个点位',
        overviewEntries: [
          { kind: 'item', tone: 'green', title: '1 才村码头', description: '上午先去人少的岸线拍湖景。' },
          { kind: 'transfer', text: '才村码头 → 喜洲古镇' },
          { kind: 'item', tone: 'green', title: '2 喜洲古镇', description: '白族建筑 + 稻田视角，适合中午前到。' },
          { kind: 'transfer', text: '喜洲古镇 → 双廊' },
          { kind: 'item', tone: 'green', title: '3 双廊', description: '下午坐海景位喝咖啡，顺便看晚霞。' },
          { kind: 'transfer', text: '双廊 → 民宿' },
          { kind: 'item', tone: 'violet', title: '继续入住大理古城山海观景民宿', description: '当晚回到同一住处，不再额外切换酒店。' },
        ],
        routeItems: [
          {
            id: 131,
            order: 1,
            tone: 'green',
            type: 'scenic',
            title: '才村码头',
            time: '08:30 - 10:10',
            description: '早晨风平浪静，适合慢拍。',
            actionLabel: '景区',
          },
          {
            id: 132,
            order: 2,
            tone: 'green',
            type: 'scenic',
            title: '喜洲古镇',
            time: '10:50 - 13:20',
            description: '稻田与白族老屋一起拍。',
            actionLabel: '景区',
          },
          {
            id: 133,
            order: 3,
            tone: 'green',
            type: 'scenic',
            title: '双廊',
            time: '15:10 - 18:10',
            description: '海景咖啡 + 傍晚海岸线。',
            actionLabel: '景区',
          },
          {
            id: 134,
            order: 4,
            tone: 'violet',
            type: 'accommodation',
            title: '继续入住大理古城山海观景民宿',
            time: '20:20',
            description: '回到同一住处，不换酒店。',
            actionLabel: '住宿',
          },
        ],
        expenses: [
          { id: 231, title: '洱海环线包车', note: '包车 · 全天', tags: ['交通'], amount: 980 },
          { id: 232, title: '菌锅晚餐', note: '阿哲代点，双人套餐', tags: ['餐饮'], amount: 168 },
          { id: 233, title: '双廊旅拍门票', note: '景区 · 2 张', tags: ['景区'], amount: 466 },
        ],
      },
    ],
    searchResults: [
      {
        id: 'poi-1',
        title: '大理古城博爱路',
        address: '云南省大理白族自治州大理市大理古城博爱路 102 号',
      },
      {
        id: 'poi-2',
        title: '大理古城南门',
        address: '云南省大理白族自治州大理市一塔路南门城楼旁',
      },
      {
        id: 'poi-3',
        title: '龙龛码头',
        address: '云南省大理白族自治州大理市环海西路龙龛村旁',
      },
    ],
  },
  {
    id: 2,
    cardTitle: '日本关西赏樱',
    cardDate: '2026.03.28 - 2026.04.05',
    heading: '京都 · 大阪 · 奈良',
    meta: '2026.03.28 - 2026.04.05 · 9 天 8 晚 · 2 人同行',
    totalExpenseAmount: 0,
    perPersonExpenseAmount: 0,
    overviewHint: '设计稿演示数据。',
    expenseHint: '设计稿演示数据。',
    overviewExpenseRows: [],
    companions: [],
    days: [],
    searchResults: [],
  },
  {
    id: 3,
    cardTitle: '周末海岛短途',
    cardDate: '2026.05.02 - 2026.05.04',
    heading: '海岛周末',
    meta: '2026.05.02 - 2026.05.04 · 3 天 2 晚 · 2 人同行',
    totalExpenseAmount: 0,
    perPersonExpenseAmount: 0,
    overviewHint: '设计稿演示数据。',
    expenseHint: '设计稿演示数据。',
    overviewExpenseRows: [],
    companions: [],
    days: [],
    searchResults: [],
  },
]

export const travelCompanionFallbackOptions = [
  { value: '1', label: '阿哲' },
  { value: '2', label: '爱玲' },
  { value: '3', label: '大川' },
]

export function getTravelDemoPlan(planId: number) {
  return travelDemoPlans.find((item) => item.id === planId) ?? travelDemoPlans[0]
}

export function buildTravelPlanListFromDemo(): TravelPlan[] {
  return travelDemoPlans.map((plan) => {
    const expenseCount = plan.days.reduce((sum, day) => sum + day.expenses.length, 0)
    const startDate = plan.cardDate.split(' - ')[0].split('.').join('-')
    const endDate = plan.cardDate.split(' - ')[1].split('.').join('-')
    return {
      id: plan.id,
      userId: 1,
      name: plan.cardTitle,
      destination: plan.heading,
      startDate,
      endDate,
      remark: null,
      status: plan.id === 1 ? 'active' : 'completed',
      sortOrder: plan.id,
      companionCount: Math.max(plan.companions.length, 1),
      travelerCount: Math.max(plan.companions.length + 1, 2),
      dayCount: plan.days.length,
      expenseCount,
      totalExpenseAmount: plan.totalExpenseAmount,
      perPersonExpenseAmount: plan.perPersonExpenseAmount,
      createdAt: '2026-03-01T00:00:00',
      updatedAt: '2026-03-01T00:00:00',
    }
  })
}

export function buildTravelPlanDetailFromDemo(planId: number): TravelPlanDetail {
  const plan = getTravelDemoPlan(planId)
  const allExpenses = plan.days.flatMap((day) => day.expenses.map((item) => expenseToApiItem(plan.id, day.id, item)))
  const startDate = plan.cardDate.split(' - ')[0].split('.').join('-')
  const endDate = plan.cardDate.split(' - ')[1].split('.').join('-')
  return {
    id: plan.id,
    userId: 1,
    name: plan.cardTitle,
    destination: plan.heading,
    startDate,
    endDate,
    remark: null,
    status: 'active',
    sortOrder: plan.id,
    overview: {
      companionCount: plan.companions.length,
      travelerCount: Math.max(plan.companions.length + 1, 2),
      dayCount: plan.days.length,
      itineraryCount: plan.days.reduce((sum, day) => sum + day.routeItems.length, 0),
      expenseCount: allExpenses.length,
      totalExpenseAmount: plan.totalExpenseAmount,
      perPersonExpenseAmount: plan.perPersonExpenseAmount,
    },
    companions: plan.companions,
    days: plan.days.map((day) => ({
      id: day.id,
      travelPlanId: plan.id,
      dayIndex: day.dayIndex,
      title: day.title,
      travelDate: null,
      sortOrder: day.dayIndex,
      itineraries: day.routeItems.map((item) => itineraryToApiItem(day.id, item)),
      expenses: day.expenses.map((item) => expenseToApiItem(plan.id, day.id, item)),
      createdAt: '2026-03-01T00:00:00',
      updatedAt: '2026-03-01T00:00:00',
    })),
    expenses: allExpenses,
    createdAt: '2026-03-01T00:00:00',
    updatedAt: '2026-03-01T00:00:00',
  }
}
