/**
 * 天气图标映射工具
 *
 * 根据天气描述和当前时间（白天/夜间）返回对应的图标路径
 * 白天：7:00 - 19:00
 * 夜间：19:00 - 次日7:00
 */
import sunny from '@/assets/png/weather/晴天.png'
import cloudy from '@/assets/png/weather/多云.png'
import overcast from '@/assets/png/weather/阴天.png'
import lightRain from '@/assets/png/weather/小雨.png'
import heavyRain from '@/assets/png/weather/大雨.png'
import thunder from '@/assets/png/weather/雷阵雨.png'
import snow from '@/assets/png/weather/雪.png'
import haze from '@/assets/png/weather/雾霾.png'
import sunnyNight from '@/assets/png/weather/晴夜.png'
import cloudyNight from '@/assets/png/weather/阴夜.png'
import rainNight from '@/assets/png/weather/雨夜.png'

/** 判断当前是否为白天（7:00-19:00） */
export function isDaytime(hour: number = new Date().getHours()): boolean {
  return hour >= 7 && hour < 19
}

/** 根据天气描述 + 当前时间返回图标路径，无匹配返回空字符串 */
export function getWeatherIcon(weather: string, hour: number = new Date().getHours()): string {
  if (!weather) return ''
  const night = !isDaytime(hour)

  // 先匹配特殊天气（不分昼夜的）
  if (/雷阵雨|雷/.test(weather)) return thunder
  if (/雪/.test(weather)) return snow
  if (/雾|霾/.test(weather)) return haze
  if (/暴雨|大雨/.test(weather)) return heavyRain

  // 雨
  if (/雨/.test(weather)) {
    return night ? rainNight : lightRain
  }

  // 晴
  if (/晴/.test(weather)) {
    return night ? sunnyNight : sunny
  }

  // 多云 / 阴
  if (/多云/.test(weather)) return cloudy
  if (/阴/.test(weather)) {
    return night ? cloudyNight : overcast
  }

  return ''
}
